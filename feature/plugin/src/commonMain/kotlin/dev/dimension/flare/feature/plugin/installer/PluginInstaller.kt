package dev.dimension.flare.feature.plugin.installer

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginCleanupResultV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.manifest.PluginManifestV1
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
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
    private var activePreview: PreparedPluginInstallV1? = null

    public suspend fun inspect(path: Path): PreparedPluginInstallV1 = fileSystem.source(path).buffer().use { source -> inspect(source) }

    public suspend fun inspect(source: Source): PreparedPluginInstallV1 =
        mutex.withLock {
            activePreview?.let { discardLocked(it) }
            ensureDirectories()
            fileSystem.delete(stateStore.paths.incoming, mustExist = false)
            val hash = stage(source)
            try {
                val validated = validator.validate(stateStore.paths.incoming)
                val packageSize = requireNotNull(fileSystem.metadata(stateStore.paths.incoming).size)
                val existing = stateStore.desired.value.plugins[validated.manifest.id]
                val preview =
                    PreparedPluginInstallV1(
                        pluginId = validated.manifest.id,
                        platformId = validated.manifest.platform.id,
                        nameFallback = validated.manifest.name.fallback,
                        version = validated.manifest.version,
                        packageHash = hash,
                        packageSize = packageSize,
                        capabilities =
                            validated.manifest.platform.capabilities.mapValues { (_, capability) ->
                                capability.operations.keys
                            },
                        permissions = validated.manifest.sensitivePermissions(),
                        warnings = buildWarnings(validated.manifest, hash, existing, validated.warnings),
                        requiresConfirmation = existing?.packageHash != hash,
                        existingVersion = existing?.version,
                        validated = validated,
                    )
                activePreview = preview
                preview
            } catch (error: Exception) {
                fileSystem.delete(stateStore.paths.incoming, mustExist = false)
                throw error
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
            val packagePath = stateStore.paths.packagePath(preview.packageHash)
            val iconPath = stateStore.paths.iconPath(preview.packageHash)
            val record =
                InstalledPluginV1(
                    pluginId = preview.pluginId,
                    version = preview.version,
                    packageHash = preview.packageHash,
                    packageSize = preview.packageSize,
                    iconSize =
                        preview.validated.icon.size
                            .toLong(),
                    source = PluginInstallSourceV1.Local,
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
            stateStore.cleanup(setOfNotNull(activePreview?.let { stateStore.paths.incoming }))
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
                    stateStore.paths.iconPath(candidate.record.packageHash),
                    candidate.icon,
                )
            }
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
                        source = PluginInstallSourceV1.Local,
                        enabled = true,
                        manifest = validated.manifest,
                        catalogs = validated.catalogs,
                    ),
                icon = validated.icon,
            )
        }.getOrNull()

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
        val temp = stateStore.paths.staging / "icon.next.png"
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
    public val pluginId: String,
    public val platformId: String,
    public val nameFallback: String,
    public val version: String,
    public val packageHash: String,
    public val packageSize: Long,
    public val capabilities: Map<String, Set<String>>,
    public val permissions: Set<PluginSensitivePermissionV1>,
    public val warnings: List<PluginInstallWarningV1>,
    public val requiresConfirmation: Boolean,
    public val existingVersion: String?,
    internal val validated: ValidatedPluginPackage,
)

public data class PluginSensitivePermissionV1(
    val type: PluginSensitivePermissionTypeV1,
    val origin: String,
    val cookieName: String? = null,
)

public enum class PluginSensitivePermissionTypeV1 {
    NetworkOrigin,
    Cookie,
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
}

private fun buildWarnings(
    manifest: PluginManifestV1,
    hash: String,
    existing: InstalledPluginV1?,
    validationWarnings: List<String>,
): List<PluginInstallWarningV1> =
    buildList {
        add(PluginInstallWarningV1(PluginInstallWarningTypeV1.UnverifiedLocal))
        if (existing != null) {
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

private fun PluginManifestV1.sensitivePermissions(): Set<PluginSensitivePermissionV1> =
    buildSet {
        add(PluginSensitivePermissionV1(PluginSensitivePermissionTypeV1.NetworkOrigin, PluginAbiV1.ACCOUNT_ORIGIN))
        permissions.authOrigins.forEach { origin ->
            add(PluginSensitivePermissionV1(PluginSensitivePermissionTypeV1.NetworkOrigin, origin))
        }
        platform.loginMethods.forEach { method ->
            method.cookie?.probes?.forEach { probe ->
                probe.cookies.forEach { cookie ->
                    add(
                        PluginSensitivePermissionV1(
                            type = PluginSensitivePermissionTypeV1.Cookie,
                            origin = probe.url,
                            cookieName = cookie.name,
                        ),
                    )
                }
            }
        }
    }

private fun compareSemVer(
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

private val dev.dimension.flare.feature.plugin.manifest.PluginTextV1.fallback: String
    get() =
        when (this) {
            is dev.dimension.flare.feature.plugin.manifest.PluginTextV1.Literal -> value
            is dev.dimension.flare.feature.plugin.manifest.PluginTextV1.Localized -> fallback
        }

private const val COPY_BUFFER_BYTES = 8_192L
private const val PACKAGE_SUFFIX = ".fpp"
private const val MAX_RECOVERY_PACKAGES = 256
private val PACKAGE_FILE = Regex("[0-9a-f]{64}\\.fpp")
