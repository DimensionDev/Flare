package dev.dimension.flare.feature.plugin.lifecycle

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.github.COMMIT_SHA
import dev.dimension.flare.feature.plugin.github.stableGitHubVersion
import dev.dimension.flare.feature.plugin.installer.FppLimits
import dev.dimension.flare.feature.plugin.installer.FppPaths
import dev.dimension.flare.feature.plugin.installer.validatePluginCatalogs
import dev.dimension.flare.feature.plugin.manifest.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class PluginStateStore private constructor(
    private val fileSystem: FileSystem,
    internal val paths: PluginStoragePaths,
    initialIndex: PluginStateIndexV1?,
    initialIssue: PluginStateIssueV1?,
) {
    private val mutex = Mutex()
    private val mutableDesired =
        MutableStateFlow(
            PluginDesiredSnapshotV1(
                plugins = initialIndex?.plugins?.associateBy(InstalledPluginV1::pluginId).orEmpty(),
                indexHealthy = initialIndex != null,
            ),
        )

    public val desired: StateFlow<PluginDesiredSnapshotV1> = mutableDesired.asStateFlow()
    public val running: PluginRunningSnapshotV1 = captureRunning(initialIndex, initialIssue)

    public suspend fun uninstall(pluginId: String) {
        mutate { index -> index.copy(plugins = index.plugins.filterNot { it.pluginId == pluginId }) }
    }

    internal suspend fun install(record: InstalledPluginV1) {
        mutate { index ->
            val plugins = index.plugins.filterNot { it.pluginId == record.pluginId } + record
            index.copy(plugins = plugins.sortedBy(InstalledPluginV1::pluginId))
        }
    }

    internal fun validateInstall(record: InstalledPluginV1) {
        val current = mutableDesired.value
        if (!current.indexHealthy) throw PluginIndexCorruptException("Plugin index is corrupt")
        val plugins = current.plugins.values.filterNot { it.pluginId == record.pluginId } + record
        validateEncodedIndex(PluginStateIndexV1(plugins = plugins.sortedBy(InstalledPluginV1::pluginId)))
    }

    internal suspend fun rebuild(records: List<InstalledPluginV1>) {
        mutex.withLock {
            require(!mutableDesired.value.indexHealthy) { "Plugin index is healthy" }
            val index = PluginStateIndexV1(plugins = records.sortedBy(InstalledPluginV1::pluginId))
            validateEncodedIndex(index)
            writeIndex(index)
            mutableDesired.value =
                PluginDesiredSnapshotV1(
                    plugins = index.plugins.associateBy(InstalledPluginV1::pluginId),
                    indexHealthy = true,
                )
        }
    }

    internal suspend fun cleanup(
        protectedStagingPaths: Set<Path>,
        activePlugins: Collection<RunningPluginV1>,
    ): PluginCleanupResultV1 =
        mutex.withLock {
            val desiredSnapshot = mutableDesired.value
            if (!desiredSnapshot.indexHealthy) {
                throw PluginIndexCorruptException("Plugin index is corrupt; cleanup is disabled")
            }
            val protectedHashes =
                activePlugins.map { it.installed.packageHash } +
                    desiredSnapshot.plugins.values.map(InstalledPluginV1::packageHash)
            val protectedIconPrefixes =
                (desiredSnapshot.plugins.keys + activePlugins.map { it.installed.pluginId })
                    .map { "platform-${it.encodeUtf8().sha256().hex()}-" }
            var packages = 0
            var icons = 0
            var staging = 0
            fileSystem.listOrNull(paths.packages)?.forEach { path ->
                val hash = path.name.removeSuffix(PACKAGE_SUFFIX)
                if (path.name.endsWith(PACKAGE_SUFFIX) && HASH.matches(hash) && hash !in protectedHashes) {
                    fileSystem.delete(path, mustExist = false)
                    packages++
                }
            }
            fileSystem.listOrNull(paths.icons)?.forEach { path ->
                val hash = path.name.substringBeforeLast('.')
                val orphanedPlatformIcon = path.name.startsWith("platform-") && protectedIconPrefixes.none { path.name.startsWith(it) }
                if ("assets/icon.${path.name.substringAfterLast('.')}" in FppPaths.ICONS &&
                    ((HASH.matches(hash) && hash !in protectedHashes) || orphanedPlatformIcon)
                ) {
                    fileSystem.delete(path, mustExist = false)
                    icons++
                }
            }
            fileSystem.listOrNull(paths.staging)?.forEach { path ->
                if (path !in protectedStagingPaths) {
                    val metadata = fileSystem.metadataOrNull(path)
                    if (metadata?.isRegularFile == true) {
                        fileSystem.delete(path, mustExist = false)
                        staging++
                    }
                }
            }
            PluginCleanupResultV1(packages = packages, icons = icons, stagingFiles = staging)
        }

    private suspend fun mutate(transform: (PluginStateIndexV1) -> PluginStateIndexV1) {
        mutex.withLock {
            val current =
                if (mutableDesired.value.indexHealthy) {
                    PluginStateIndexV1(
                        plugins =
                            mutableDesired.value.plugins.values
                                .sortedBy(InstalledPluginV1::pluginId),
                    )
                } else {
                    throw PluginIndexCorruptException("Plugin index is corrupt")
                }
            val updated = transform(current)
            validateEncodedIndex(updated)
            writeIndex(updated)
            mutableDesired.value =
                PluginDesiredSnapshotV1(
                    plugins = updated.plugins.associateBy(InstalledPluginV1::pluginId),
                    indexHealthy = true,
                )
        }
    }

    private fun writeIndex(index: PluginStateIndexV1) {
        val bytes = PluginJsonV1.encodeToString(index).encodeToByteArray()
        fileSystem.createDirectories(paths.root)
        fileSystem.createDirectories(paths.staging)
        fileSystem.write(paths.indexTemp) { write(bytes) }
        try {
            fileSystem.atomicMove(paths.indexTemp, paths.index)
        } catch (error: Exception) {
            fileSystem.delete(paths.indexTemp, mustExist = false)
            throw error
        }
    }

    private fun captureRunning(
        index: PluginStateIndexV1?,
        initialIssue: PluginStateIssueV1?,
    ): PluginRunningSnapshotV1 {
        if (index == null) {
            return PluginRunningSnapshotV1(
                plugins = emptyMap(),
                issues = listOfNotNull(initialIssue),
            )
        }
        val issues = mutableListOf<PluginStateIssueV1>()
        val plugins = mutableMapOf<String, RunningPluginV1>()
        index.plugins.forEach { installed ->
            val packagePath = paths.packagePath(installed.packageHash)
            val iconPath = paths.iconPath(installed.packageHash, installed.iconExtension)
            val packageValid = fileSystem.metadataOrNull(packagePath)?.let { it.isRegularFile && it.size == installed.packageSize } == true
            val iconValid = fileSystem.metadataOrNull(iconPath)?.let { it.isRegularFile && it.size == installed.iconSize } == true
            if (!packageValid || !iconValid) {
                issues +=
                    PluginStateIssueV1(
                        code = if (!packageValid) "package.missing-or-changed" else "icon.missing-or-changed",
                        pluginId = installed.pluginId,
                        message = "Plugin files are missing or changed",
                    )
            } else {
                plugins[installed.pluginId] =
                    RunningPluginV1(
                        installed = installed,
                        packagePath = packagePath.toString(),
                        iconPath = fileSystem.persistPlatformIcon(installed.pluginId, iconPath).toString(),
                        generation = installed.runtimeGeneration,
                    )
            }
        }
        return PluginRunningSnapshotV1(
            plugins = plugins,
            issues = issues,
        )
    }

    public companion object {
        public fun open(
            fileSystem: FileSystem,
            root: Path,
        ): PluginStateStore {
            val paths = PluginStoragePaths(root)
            if (!fileSystem.exists(paths.index)) {
                return PluginStateStore(fileSystem, paths, PluginStateIndexV1(), null)
            }
            return try {
                val bytes = fileSystem.readBounded(paths.index, FppLimits.MAX_INDEX_BYTES)
                val index = PluginJsonV1.decodeFromString<PluginStateIndexV1>(bytes.decodeToString(throwOnInvalidSequence = true))
                validateIndex(index)
                PluginStateStore(fileSystem, paths, index, null)
            } catch (error: Exception) {
                PluginStateStore(
                    fileSystem = fileSystem,
                    paths = paths,
                    initialIndex = null,
                    initialIssue =
                        PluginStateIssueV1(
                            code = "index.corrupt",
                            message = error.message ?: "Plugin index is corrupt",
                        ),
                )
            }
        }
    }
}

public data class PluginCleanupResultV1(
    val packages: Int,
    val icons: Int,
    val stagingFiles: Int,
)

internal data class PluginStoragePaths(
    val root: Path,
) {
    val index: Path = root / "index.json"
    val indexTemp: Path = root / "staging" / "index.next.json"
    val packages: Path = root / "packages"
    val icons: Path = root / "icons"
    val staging: Path = root / "staging"
    val incoming: Path = staging / "incoming.fpp"

    fun packagePath(hash: String): Path = packages / "$hash$PACKAGE_SUFFIX"

    fun iconPath(
        hash: String,
        extension: String = "png",
    ): Path = icons / "$hash.$extension"
}

internal fun FileSystem.persistPlatformIcon(
    pluginId: String,
    source: Path,
): Path =
    runCatching {
        val directory = requireNotNull(source.parent)
        val bytes = readBounded(source, FppLimits.MAX_ICON_BYTES)
        val extension = source.name.substringAfterLast('.')
        val target = directory / "platform-${pluginId.encodeUtf8().sha256().hex()}-${bytes.toByteString().sha256().hex()}.$extension"
        if (!exists(target)) {
            val temp = directory / "${target.name}.next"
            write(temp) { write(bytes) }
            try {
                atomicMove(temp, target)
            } catch (error: Exception) {
                delete(temp, mustExist = false)
                throw error
            }
        }
        target
    }.getOrDefault(source)

private fun validateIndex(index: PluginStateIndexV1) {
    require(index.schemaVersion == PluginStateIndexV1.SCHEMA_VERSION) { "Unsupported plugin index schema" }
    require(index.plugins.size <= FppLimits.MAX_PLUGIN_COUNT) { "Too many installed plugins" }
    require(
        index.plugins
            .map(InstalledPluginV1::pluginId)
            .distinct()
            .size == index.plugins.size,
    ) { "Duplicate plugin ID" }
    index.plugins.forEach { plugin ->
        require(plugin.runtimeGeneration >= 0) { "Invalid plugin runtime generation" }
        require(plugin.pluginId == plugin.manifest.id && plugin.version == plugin.manifest.version) { "Plugin identity mismatch" }
        (plugin.source as? PluginInstallSourceV1.GitHub)?.let { source ->
            source.repository.requireValid()
            require(
                stableGitHubVersion(source.tag) == plugin.version && COMMIT_SHA.matches(source.commitSha),
            ) { "Invalid GitHub installation source" }
        }
        require(HASH.matches(plugin.packageHash)) { "Invalid package hash" }
        require(plugin.packageSize in 1..FppLimits.MAX_PACKAGE_BYTES) { "Invalid package size" }
        require(plugin.iconSize in 1..FppLimits.MAX_ICON_BYTES) { "Invalid icon size" }
        require("assets/icon.${plugin.iconExtension}" in FppPaths.ICONS) { "Invalid icon extension" }
        plugin.manifest.validate().requireValid()
        validatePluginCatalogs(plugin.manifest, plugin.catalogs)
    }
}

private fun validateEncodedIndex(index: PluginStateIndexV1) {
    validateIndex(index)
    require(PluginJsonV1.encodeToString(index).encodeToByteArray().size <= FppLimits.MAX_INDEX_BYTES) {
        "Plugin index exceeds its size limit"
    }
}

private fun FileSystem.readBounded(
    path: Path,
    limit: Long,
): ByteArray {
    val metadata = metadataOrNull(path) ?: error("File does not exist: $path")
    val size = metadata.size
    require(metadata.isRegularFile && size != null && size <= limit) { "File exceeds its size limit" }
    return source(path).buffer().use { source ->
        val buffer = Buffer()
        while (true) {
            val remaining = limit + 1 - buffer.size
            require(remaining > 0) { "File exceeds its size limit" }
            val read = source.read(buffer, remaining)
            if (read == -1L) break
        }
        require(buffer.size <= limit) { "File exceeds its size limit" }
        buffer.readByteArray()
    }
}

private const val PACKAGE_SUFFIX = ".fpp"
private val HASH = Regex("[0-9a-f]{64}")
