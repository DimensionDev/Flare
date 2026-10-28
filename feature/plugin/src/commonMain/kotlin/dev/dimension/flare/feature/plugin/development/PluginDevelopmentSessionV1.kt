package dev.dimension.flare.feature.plugin.development

import dev.dimension.flare.data.model.PluginPermission
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.installer.FppPaths
import dev.dimension.flare.feature.plugin.installer.PluginDevelopmentSourceV1
import dev.dimension.flare.feature.plugin.installer.PluginPackageValidator
import dev.dimension.flare.feature.plugin.installer.ValidatedPluginPackage
import dev.dimension.flare.feature.plugin.installer.sensitivePermissions
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginRuntimeSwitcherV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.fallbackText
import dev.dimension.flare.model.PlatformRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import kotlin.native.HiddenFromObjC

public data class PluginDevelopmentStateV1(
    val directory: String? = null,
    val recentDirectory: String? = null,
    val pluginId: String? = null,
    val name: String? = null,
    val active: Boolean = false,
    val busy: Boolean = false,
    val validating: Boolean = false,
    val candidateHash: String? = null,
    val runningHash: String? = null,
    val generation: Long = 0,
    val error: String? = null,
    val reviewHash: String? = null,
    val permissions: List<PluginPermission> = emptyList(),
    val credentialSchemaChanged: Boolean = false,
    val messages: List<String> = emptyList(),
)

/** Owns one in-process development override; the installed index is never modified. */
@HiddenFromObjC
public class PluginDevelopmentSessionV1 internal constructor(
    private val subsystem: PluginSubsystemV1,
    private val registry: PlatformRegistry,
    accounts: AccountService,
    private val scope: CoroutineScope,
) {
    private val files = subsystem.appFiles.fileSystem
    private val root = subsystem.appFiles.directory("plugin-development")
    private val recentFile = root / "recent-directory.txt"
    private val mutableState =
        subsystem.mutableDevelopmentState.also { state ->
            state.value =
                state.value.copy(
                    recentDirectory =
                        runCatching {
                            if (files.metadataOrNull(recentFile)?.size in 1L..8192L) files.read(recentFile) { readUtf8() } else null
                        }.getOrNull(),
                )
        }
    public val state: StateFlow<PluginDevelopmentStateV1> = mutableState.asStateFlow()
    private val mutex = subsystem.mutationMutex
    private val switcher = PluginRuntimeSwitcherV1(subsystem, registry, accounts, scope)
    private var watcher: Job? = null
    private var candidate: Candidate? = null
    private var lastCheckedHash: String? = null
    private var baseline: RunningPluginV1? = null
    private var active: RunningPluginV1? = null
    private var generation = 0L

    public suspend fun open(directory: String): Unit =
        action {
            check(active == null) { "Stop development before choosing another directory" }
            require(directory.length in 1..8192 && directory.toPath().isAbsolute) { "Select an absolute plugin directory" }
            watcher?.cancel()
            candidate = null
            lastCheckedHash = null
            mutableState.value = PluginDevelopmentStateV1(directory = directory, recentDirectory = directory, busy = true)
            files.createDirectories(root)
            files.write(recentFile) { writeUtf8(directory) }
            watcher =
                scope.launch(Dispatchers.Default) {
                    // shortcut: poll bounded build inputs; add native watching only if large plugins make this costly.
                    while (isActive) {
                        delay(750)
                        mutex.withLock {
                            if (state.value.directory != directory) return@launch
                            validateDirectory(directory)
                        }
                    }
                }
            validateDirectory(directory)
        }

    public suspend fun reload(confirmed: Boolean = false): Unit =
        action {
            validateDirectory(checkNotNull(state.value.directory))
            val prepared = checkNotNull(candidate) { "Fix validation errors before reloading" }
            require(
                !confirmed || state.value.reviewHash == prepared.source.hash,
            ) { "Plugin files changed; review the new version before reloading" }
            val previous = active ?: subsystem.running.value.plugins[prepared.validated.manifest.id]
            val permissions = prepared.validated.manifest.sensitivePermissions()
            val changedSchema =
                previous != null && previous.installed.manifest.platform.credentialSchemaVersion !=
                    prepared.validated.manifest.platform.credentialSchemaVersion
            val needsReview =
                active == null || changedSchema ||
                    permissions.any {
                        it !in
                            previous
                                ?.installed
                                ?.manifest
                                ?.sensitivePermissions()
                                .orEmpty()
                    }
            if (needsReview && (!confirmed || state.value.reviewHash != prepared.source.hash)) {
                mutableState.update {
                    it.copy(reviewHash = prepared.source.hash, permissions = permissions.toList(), credentialSchemaChanged = changedSchema)
                }
                return@action
            }
            if (active == null) baseline = previous
            val replacement = prepared.running(++generation)
            switch(previous, replacement)
            active = replacement
            pruneIcons(replacement)
            mutableState.update {
                it.copy(
                    active = true,
                    runningHash = replacement.installed.packageHash,
                    generation = generation,
                    reviewHash = null,
                    credentialSchemaChanged = false,
                    error = null,
                )
            }
        }

    public fun cancelReview() {
        mutableState.update { it.copy(reviewHash = null) }
    }

    public suspend fun stop(): Unit =
        action {
            val previous = active
            if (previous != null) {
                val restored = baseline?.copy(generation = ++generation)
                switch(previous, restored)
            }
            active = null
            baseline = null
            candidate = null
            watcher?.cancel()
            watcher = null
            pruneIcons(null)
            mutableState.value = PluginDevelopmentStateV1(recentDirectory = state.value.recentDirectory, busy = true)
        }

    private suspend fun validateDirectory(directory: String) {
        try {
            val source =
                try {
                    PluginDevelopmentSourceV1.read(files, directory.toPath())
                } catch (error: Exception) {
                    lastCheckedHash = null
                    throw error
                }
            if (source.hash == lastCheckedHash) {
                candidate?.let { validateIdentity(it.validated) }
                return
            }
            candidate = null
            mutableState.update { it.copy(validating = true, candidateHash = null, reviewHash = null, error = null) }
            delay(200)
            if (source.hash != PluginDevelopmentSourceV1.read(files, directory.toPath()).hash) return
            lastCheckedHash = source.hash
            val validated = PluginPackageValidator(files).validate(source.archive)
            validateIdentity(validated)
            candidate = Candidate(source, validated)
            mutableState.update {
                it.copy(
                    pluginId = validated.manifest.id,
                    name = validated.manifest.name.fallbackText,
                    candidateHash = source.hash,
                    error = null,
                )
            }
        } catch (error: CancellationException) {
            lastCheckedHash = null
            throw error
        } catch (error: Exception) {
            candidate = null
            mutableState.update { it.copy(candidateHash = null, reviewHash = null, error = error.message?.take(512)) }
        } finally {
            mutableState.update { it.copy(validating = false) }
        }
    }

    private fun validateIdentity(validated: ValidatedPluginPackage) {
        try {
            val current = active
            if (current != null) {
                require(
                    validated.manifest.id == current.installed.pluginId &&
                        validated.manifest.platform.id == current.installed.manifest.platform.id,
                ) {
                    "Stop development before changing the plugin or platform ID"
                }
            }
            val existing = subsystem.running.value.plugins[validated.manifest.id]
            val installed = subsystem.stateStore.desired.value.plugins[validated.manifest.id]
            require(
                (existing == null || existing.installed.manifest.platform.id == validated.manifest.platform.id) &&
                    (installed == null || installed.manifest.platform.id == validated.manifest.platform.id),
            ) {
                "Development code must keep the installed plugin's platform ID"
            }
            val conflict = registry.all.firstOrNull { it.platformId.equals(validated.manifest.platform.id, true) }
            require(
                conflict == null || existing
                    ?.installed
                    ?.manifest
                    ?.platform
                    ?.id == conflict.platformId,
            ) {
                "Platform ID is already registered by another plugin or a built-in platform"
            }
        } catch (error: Exception) {
            lastCheckedHash = null
            throw error
        }
    }

    private fun pruneIcons(plugin: RunningPluginV1?) {
        val icons = root / "icons"
        runCatching {
            if (!files.exists(icons)) return@runCatching
            files
                .list(icons)
                .filter {
                    Regex("[a-f0-9]{64}\\.png").matches(it.name) && it.toString() != plugin?.iconPath
                }.forEach { runCatching { files.delete(it) } }
        }
    }

    private suspend fun switch(
        previous: RunningPluginV1?,
        replacement: RunningPluginV1?,
    ) {
        val pluginId = requireNotNull(replacement?.installed?.pluginId ?: previous?.installed?.pluginId)
        try {
            switcher.switch(previous, replacement, temporary = true)
        } catch (error: Throwable) {
            val restored = subsystem.running.value.plugins[pluginId]
            generation = maxOf(generation, restored?.generation ?: 0)
            if (active != null) {
                active = restored
                mutableState.update { it.copy(generation = generation, runningHash = restored?.installed?.packageHash) }
            }
            throw error
        }
    }

    private suspend fun action(block: suspend () -> Unit): Unit =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                mutableState.update { it.copy(busy = true, error = null) }
                try {
                    block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableState.update { it.copy(error = error.message?.take(512)) }
                    throw error
                } finally {
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }

    private inner class Candidate(
        val source: PluginDevelopmentSourceV1,
        val validated: ValidatedPluginPackage,
    ) {
        fun running(generation: Long): RunningPluginV1 {
            val directory = root / "icons"
            files.createDirectories(directory)
            val icon = directory / "${source.hash}.png"
            files.write(icon) { write(validated.icon) }
            return RunningPluginV1(
                installed =
                    InstalledPluginV1(
                        pluginId = validated.manifest.id,
                        version = validated.manifest.version,
                        packageHash = source.hash,
                        packageSize =
                            source.archive.files.values
                                .sumOf { it.size.toLong() },
                        iconSize = validated.icon.size.toLong(),
                        manifest = validated.manifest,
                        catalogs = validated.catalogs,
                    ),
                packagePath = directory.toString(),
                iconPath = icon.toString(),
                developmentSource =
                    source.archive.files
                        .getValue(FppPaths.ENTRY)
                        .decodeToString(throwOnInvalidSequence = true),
                generation = generation,
            ).also { plugin ->
                plugin.diagnostic = { message ->
                    mutableState.update { state ->
                        if (state.generation != generation) {
                            state
                        } else {
                            state.copy(messages = (state.messages + "#$generation $message").takeLast(40))
                        }
                    }
                }
            }
        }
    }
}
