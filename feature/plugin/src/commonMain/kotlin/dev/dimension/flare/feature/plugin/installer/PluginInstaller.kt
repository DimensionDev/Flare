package dev.dimension.flare.feature.plugin.installer

import dev.dimension.flare.data.model.PluginPermission
import dev.dimension.flare.data.model.PluginPermissionType
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.github.COMMIT_SHA
import dev.dimension.flare.feature.plugin.github.stableGitHubVersion
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginCleanupResultV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.manifest.PluginManifestV1
import dev.dimension.flare.feature.plugin.manifest.fallbackText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.HashingSink
import okio.HashingSource
import okio.Path
import okio.Source
import okio.buffer
import okio.use
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class PluginInstaller(
    private val fileSystem: FileSystem,
    private val stateStore: PluginStateStore,
) {
    private val validator = PluginPackageValidator(fileSystem)
    private val mutex = Mutex()
    private val preparationMutex = Mutex()
    private var preparing = false
    private var activePreview: PreparedPluginInstallV1? = null

    /** Authored plugins already have an immutable, reviewed candidate; do not consume a file-import preview. */
    internal suspend fun persistAuthored(
        validated: ValidatedPluginPackage,
        archive: ByteArray,
        enabled: Boolean,
        generation: Long,
    ): InstalledPluginV1 =
        mutex.withLock {
            val hash = archive.toByteString().sha256().hex()
            val record =
                InstalledPluginV1(
                    pluginId = validated.manifest.id,
                    version = validated.manifest.version,
                    packageHash = hash,
                    packageSize = archive.size.toLong(),
                    iconSize = validated.icon.size.toLong(),
                    iconExtension = validated.iconExtension,
                    manifest = validated.manifest,
                    catalogs = validated.catalogs,
                    enabled = enabled,
                    runtimeGeneration = generation,
                )
            stateStore.validateInstall(record)
            ensureDirectories()
            fileSystem.createDirectories(stateStore.paths.packages)
            val temporary = stateStore.paths.staging / "$hash.authored"
            try {
                fileSystem.write(temporary) { write(archive) }
                fileSystem.atomicMove(temporary, stateStore.paths.packagePath(hash))
                persistIcon(stateStore.paths.iconPath(hash, record.iconExtension), validated.icon)
            } finally {
                fileSystem.delete(temporary, mustExist = false)
            }
            record
        }

    public suspend fun inspect(
        path: Path,
        installSource: PluginInstallSourceV1 = PluginInstallSourceV1.Local,
        expectedPluginId: String? = null,
    ): PreparedPluginInstallV1 = fileSystem.source(path).buffer().use { source -> inspect(source, installSource, expectedPluginId) }

    public suspend fun inspect(
        source: Source,
        installSource: PluginInstallSourceV1 = PluginInstallSourceV1.Local,
        expectedPluginId: String? = null,
    ): PreparedPluginInstallV1 =
        preparationMutex.withLock {
            mutex.withLock {
                activePreview?.let { discardLocked(it) }
                ensureDirectories()
                fileSystem.delete(stateStore.paths.incoming, mustExist = false)
                preparing = true
            }
            try {
                val stagedHash = stage(source)
                val hash =
                    if (installSource is PluginInstallSourceV1.GitHub) {
                        installSource.repository.requireValid()
                        require(COMMIT_SHA.matches(installSource.commitSha)) { "Invalid GitHub tag commit" }
                        val files = FppArchiveReader(fileSystem).read(stateStore.paths.incoming, repositoryArchive = true).files
                        fileSystem.write(stateStore.paths.incoming) { write(encodePluginArchive(files)) }
                        hash(stateStore.paths.incoming)
                    } else {
                        stagedHash
                    }
                val validated = validator.validate(stateStore.paths.incoming)
                require(expectedPluginId == null || validated.manifest.id == expectedPluginId) { "GitHub update changed the plugin ID" }
                if (installSource is PluginInstallSourceV1.GitHub) {
                    require(
                        stableGitHubVersion(installSource.tag) == validated.manifest.version,
                    ) { "GitHub tag does not match manifest.version" }
                }
                val packageSize = requireNotNull(fileSystem.metadata(stateStore.paths.incoming).size)
                val existing = stateStore.desired.value.plugins[validated.manifest.id]
                require(existing == null || existing.manifest.platform.id == validated.manifest.platform.id) {
                    "Plugin platform ID cannot change during an update"
                }
                if (expectedPluginId != null && installSource is PluginInstallSourceV1.GitHub) {
                    require((existing?.source as? PluginInstallSourceV1.GitHub)?.repository?.id == installSource.repository.id) {
                        "GitHub update changed the installation source"
                    }
                }
                val preview =
                    PreparedPluginInstallV1(
                        packageHash = hash,
                        packageSize = packageSize,
                        warnings = buildWarnings(validated.manifest, hash, existing, validated.warnings, installSource),
                        requiresConfirmation = existing?.packageHash != hash || existing.source != installSource,
                        existingVersion = existing?.version,
                        source = installSource,
                        validated = validated,
                    )
                mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    activePreview = preview
                    preview
                }
            } finally {
                withContext(NonCancellable) {
                    mutex.withLock {
                        preparing = false
                        if (activePreview == null) fileSystem.delete(stateStore.paths.incoming, mustExist = false)
                    }
                }
            }
        }

    public suspend fun commit(
        preview: PreparedPluginInstallV1,
        confirmed: Boolean,
    ): InstalledPluginV1 =
        mutex.withLock {
            require(activePreview === preview) { "Install preview is no longer active" }
            require(!preview.requiresConfirmation || confirmed) { "Installation was not confirmed" }
            val existing = stateStore.desired.value.plugins[preview.pluginId]
            require(existing == null || existing.manifest.platform.id == preview.platformId) {
                "Plugin platform ID cannot change during an update"
            }
            val packagePath = stateStore.paths.packagePath(preview.packageHash)
            val iconPath = stateStore.paths.iconPath(preview.packageHash, preview.validated.iconExtension)
            val record =
                InstalledPluginV1(
                    pluginId = preview.pluginId,
                    version = preview.version,
                    packageHash = preview.packageHash,
                    packageSize = preview.packageSize,
                    iconSize =
                        preview.validated.icon.size
                            .toLong(),
                    iconExtension = preview.validated.iconExtension,
                    source = preview.source,
                    enabled = existing?.enabled ?: true,
                    manifest = preview.validated.manifest,
                    catalogs = preview.validated.catalogs,
                )
            stateStore.validateInstall(record)
            persistPackage(packagePath, preview.packageSize)
            persistIcon(iconPath, preview.validated.icon)
            stateStore.install(record)
            activePreview = null
            record
        }

    public suspend fun discard(preview: PreparedPluginInstallV1) {
        mutex.withLock {
            if (activePreview === preview) discardLocked(preview)
        }
    }

    public suspend fun cleanup(): PluginCleanupResultV1 =
        mutex.withLock {
            stateStore.cleanup(setOfNotNull(stateStore.paths.incoming.takeIf { preparing || activePreview != null }))
        }

    /** Explicit recovery for a corrupt index. It never runs during startup. */
    public suspend fun rebuildIndex(): PluginIndexRebuildResultV1 =
        mutex.withLock {
            require(!stateStore.desired.value.indexHealthy) { "Plugin index is healthy" }
            val packagePaths =
                fileSystem
                    .listOrNull(stateStore.paths.packages)
                    .orEmpty()
                    .filter { PACKAGE_FILE.matches(it.name) }
                    .sortedBy(Path::name)
            require(packagePaths.size <= MAX_RECOVERY_PACKAGES) { "Too many plugin packages to recover" }

            var skipped = 0
            val recovered = mutableMapOf<String, RecoveredPlugin>()
            packagePaths.forEach { path ->
                val candidate = recover(path)
                if (candidate == null) {
                    skipped++
                } else {
                    val current = recovered[candidate.record.pluginId]
                    if (current == null || candidate.isNewerThan(current)) {
                        if (current != null) skipped++
                        recovered[candidate.record.pluginId] = candidate
                    } else {
                        skipped++
                    }
                }
            }

            recovered.values.forEach { candidate ->
                persistIcon(
                    stateStore.paths.iconPath(candidate.record.packageHash, candidate.record.iconExtension),
                    candidate.icon,
                )
            }
            currentCoroutineContext().ensureActive()
            stateStore.rebuild(recovered.values.map(RecoveredPlugin::record))
            PluginIndexRebuildResultV1(restored = recovered.size, skipped = skipped)
        }

    private fun stage(source: Source): String {
        val hashingSink = HashingSink.sha256(fileSystem.sink(stateStore.paths.incoming))
        try {
            hashingSink.buffer().use { sink ->
                val buffer = Buffer()
                var total = 0L
                while (true) {
                    val read = source.read(buffer, COPY_BUFFER_BYTES)
                    if (read == -1L) break
                    total += read
                    require(total <= FppLimits.MAX_PACKAGE_BYTES) { "Package exceeds the compressed size limit" }
                    sink.write(buffer, read)
                }
            }
            return hashingSink.hash.hex()
        } catch (error: Exception) {
            fileSystem.delete(stateStore.paths.incoming, mustExist = false)
            throw error
        }
    }

    private suspend fun recover(path: Path): RecoveredPlugin? =
        runCatching {
            val packageHash = path.name.removeSuffix(PACKAGE_SUFFIX)
            require(hash(path) == packageHash) { "Plugin package hash does not match its filename" }
            val packageSize = requireNotNull(fileSystem.metadata(path).size)
            require(packageSize in 1..FppLimits.MAX_PACKAGE_BYTES) { "Plugin package size is invalid" }
            val validated = validator.validate(path)
            RecoveredPlugin(
                record =
                    InstalledPluginV1(
                        pluginId = validated.manifest.id,
                        version = validated.manifest.version,
                        packageHash = packageHash,
                        packageSize = packageSize,
                        iconSize = validated.icon.size.toLong(),
                        iconExtension = validated.iconExtension,
                        source = PluginInstallSourceV1.Local,
                        enabled = true,
                        manifest = validated.manifest,
                        catalogs = validated.catalogs,
                    ),
                icon = validated.icon,
            )
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }

    private fun hash(path: Path): String {
        val hashingSource = HashingSource.sha256(fileSystem.source(path))
        hashingSource.buffer().use { source ->
            val buffer = Buffer()
            while (source.read(buffer, COPY_BUFFER_BYTES) != -1L) {
                buffer.skip(buffer.size)
            }
        }
        return hashingSource.hash.hex()
    }

    private fun persistPackage(
        target: Path,
        expectedSize: Long,
    ) {
        fileSystem.createDirectories(stateStore.paths.packages)
        if (fileSystem.exists(stateStore.paths.incoming)) {
            fileSystem.atomicMove(stateStore.paths.incoming, target)
        }
        require(fileSystem.metadataOrNull(target)?.size == expectedSize) { "Installed package file is invalid" }
    }

    private fun persistIcon(
        target: Path,
        icon: ByteArray,
    ) {
        fileSystem.createDirectories(stateStore.paths.icons)
        val temp = stateStore.paths.staging / "icon.next"
        fileSystem.write(temp) { write(icon) }
        try {
            fileSystem.atomicMove(temp, target)
        } catch (error: Exception) {
            fileSystem.delete(temp, mustExist = false)
            throw error
        }
    }

    private fun ensureDirectories() {
        fileSystem.createDirectories(stateStore.paths.staging)
    }

    private fun discardLocked(preview: PreparedPluginInstallV1) {
        if (activePreview === preview) {
            fileSystem.delete(stateStore.paths.incoming, mustExist = false)
            activePreview = null
        }
    }
}

public class PreparedPluginInstallV1 internal constructor(
    public val packageHash: String,
    public val packageSize: Long,
    public val warnings: List<PluginInstallWarningV1>,
    public val requiresConfirmation: Boolean,
    public val existingVersion: String?,
    public val source: PluginInstallSourceV1,
    internal val validated: ValidatedPluginPackage,
) {
    public val pluginId: String get() = validated.manifest.id
    public val platformId: String get() = validated.manifest.platform.id
    public val nameFallback: String get() = validated.manifest.name.fallbackText
    public val version: String get() = validated.manifest.version
    public val capabilities: Map<String, Set<String>> =
        validated.manifest.platform.capabilities
            .mapValues { (_, capability) -> capability.operations.keys }
    public val permissions: Set<PluginPermission> = validated.manifest.sensitivePermissions()
}

public data class PluginInstallWarningV1(
    val type: PluginInstallWarningTypeV1,
    val detail: String? = null,
)

public data class PluginIndexRebuildResultV1(
    val restored: Int,
    val skipped: Int,
)

public enum class PluginInstallWarningTypeV1 {
    UnverifiedLocal,
    AddedPermission,
    Downgrade,
    SameVersionDifferentHash,
    Compatibility,
    SourceChanged,
    TagMoved,
}

private fun buildWarnings(
    manifest: PluginManifestV1,
    hash: String,
    existing: InstalledPluginV1?,
    validationWarnings: List<String>,
    source: PluginInstallSourceV1,
): List<PluginInstallWarningV1> =
    buildList {
        if (source == PluginInstallSourceV1.Local) add(PluginInstallWarningV1(PluginInstallWarningTypeV1.UnverifiedLocal))
        if (existing != null) {
            val sourceChanged =
                when (val previous = existing.source) {
                    PluginInstallSourceV1.Local -> source != PluginInstallSourceV1.Local
                    is PluginInstallSourceV1.GitHub -> (source as? PluginInstallSourceV1.GitHub)?.repository?.id != previous.repository.id
                }
            if (sourceChanged) add(PluginInstallWarningV1(PluginInstallWarningTypeV1.SourceChanged))
            val previousGitHub = existing.source as? PluginInstallSourceV1.GitHub
            if (source is PluginInstallSourceV1.GitHub && previousGitHub != null &&
                previousGitHub.repository.id == source.repository.id && previousGitHub.tag == source.tag &&
                previousGitHub.commitSha != source.commitSha
            ) {
                add(PluginInstallWarningV1(PluginInstallWarningTypeV1.TagMoved, source.tag))
            }
            val addedPermissions = manifest.sensitivePermissions() - existing.manifest.sensitivePermissions()
            addedPermissions.forEach { permission ->
                add(
                    PluginInstallWarningV1(
                        type = PluginInstallWarningTypeV1.AddedPermission,
                        detail = permission.cookieName?.let { "${permission.origin}: $it" } ?: permission.origin,
                    ),
                )
            }
            val comparison = compareSemVer(manifest.version, existing.version)
            if (comparison < 0) add(PluginInstallWarningV1(PluginInstallWarningTypeV1.Downgrade, existing.version))
            if (manifest.version == existing.version && hash != existing.packageHash) {
                add(PluginInstallWarningV1(PluginInstallWarningTypeV1.SameVersionDifferentHash))
            }
        }
        validationWarnings.forEach { add(PluginInstallWarningV1(PluginInstallWarningTypeV1.Compatibility, it)) }
    }

internal fun PluginManifestV1.sensitivePermissions(): Set<PluginPermission> =
    buildSet {
        add(PluginPermission(PluginPermissionType.NetworkOrigin, PluginAbiV1.ACCOUNT_ORIGIN))
        permissions.authOrigins.forEach { origin ->
            add(PluginPermission(PluginPermissionType.NetworkOrigin, origin))
        }
        if (permissions.mediaNetwork) {
            add(PluginPermission(PluginPermissionType.MediaNetwork, "*"))
        }
        platform.loginMethods.forEach { method ->
            method.cookie?.probes?.forEach { probe ->
                probe.cookies.forEach { cookie ->
                    add(
                        PluginPermission(
                            type = PluginPermissionType.Cookie,
                            origin = probe.url,
                            cookieName = cookie.name,
                        ),
                    )
                }
            }
        }
    }

internal fun compareSemVer(
    first: String,
    second: String,
): Int {
    val left = SemVer.parse(first)
    val right = SemVer.parse(second)
    left.core.zip(right.core).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
    if (left.preRelease.isEmpty() || right.preRelease.isEmpty()) {
        return when {
            left.preRelease.isEmpty() && right.preRelease.isNotEmpty() -> 1
            left.preRelease.isNotEmpty() && right.preRelease.isEmpty() -> -1
            else -> 0
        }
    }
    val count = maxOf(left.preRelease.size, right.preRelease.size)
    repeat(count) { index ->
        val a = left.preRelease.getOrNull(index) ?: return -1
        val b = right.preRelease.getOrNull(index) ?: return 1
        if (a != b) {
            val aNumber = a.toLongOrNull()
            val bNumber = b.toLongOrNull()
            return when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                aNumber != null -> -1
                bNumber != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
    return 0
}

private data class SemVer(
    val core: List<Long>,
    val preRelease: List<String>,
) {
    companion object {
        fun parse(value: String): SemVer {
            val withoutBuild = value.substringBefore('+')
            return SemVer(
                core = withoutBuild.substringBefore('-').split('.').map(String::toLong),
                preRelease = withoutBuild.substringAfter('-', "").split('.').filter(String::isNotEmpty),
            )
        }
    }
}

private data class RecoveredPlugin(
    val record: InstalledPluginV1,
    val icon: ByteArray,
) {
    fun isNewerThan(other: RecoveredPlugin): Boolean {
        val version = compareSemVer(record.version, other.record.version)
        return version > 0 || (version == 0 && record.packageHash > other.record.packageHash)
    }
}

private const val COPY_BUFFER_BYTES = 8_192L
private const val PACKAGE_SUFFIX = ".fpp"
private const val MAX_RECOVERY_PACKAGES = 256
private val PACKAGE_FILE = Regex("[0-9a-f]{64}\\.fpp")
