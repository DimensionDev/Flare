package dev.dimension.flare.feature.plugin.lifecycle

import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.adapter.PluginPlatformSpecSourceV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.model.PlatformRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString

/** Caller holds the subsystem mutation lock; failed replacements restore fresh sessions. */
internal class PluginRuntimeSwitcherV1(
    private val subsystem: PluginSubsystemV1,
    private val registry: PlatformRegistry,
    private val accounts: AccountService,
    private val scope: CoroutineScope,
) {
    suspend fun install(record: InstalledPluginV1) {
        val previous = subsystem.running.value.plugins[record.pluginId]
        check(previous?.developmentSource == null) { "Stop development before changing the installed plugin" }
        check(!subsystem.oauth.hasPending(record.pluginId)) { "Wait for login to finish" }
        val installed = record.copy(runtimeGeneration = maxOf(previous?.generation ?: 0, record.runtimeGeneration) + 1)
        switch(previous, running(installed))
    }

    suspend fun uninstall(
        pluginId: String,
        platformId: String,
    ) {
        val previous = subsystem.running.value.plugins[pluginId]
        check(previous?.developmentSource == null) { "Stop development before deleting this plugin" }
        switch(previous, null, pluginId = pluginId, platformId = platformId)
        // The running snapshot no longer protects retired packages or their account icons.
        subsystem.installer.cleanup(subsystem.running.value.plugins.values)
    }

    fun running(record: InstalledPluginV1): RunningPluginV1 =
        RunningPluginV1(
            record,
            subsystem.stateStore.paths
                .packagePath(record.packageHash)
                .toString(),
            subsystem.appFiles.fileSystem
                .persistPlatformIcon(
                    record.pluginId,
                    subsystem.stateStore.paths.iconPath(record.packageHash, record.iconExtension),
                ).toString(),
            generation = record.runtimeGeneration,
        )

    suspend fun switch(
        previous: RunningPluginV1?,
        replacement: RunningPluginV1?,
        installed: InstalledPluginV1? = replacement?.installed,
        pluginId: String =
            requireNotNull(installed?.pluginId ?: previous?.installed?.pluginId),
        temporary: Boolean = false,
        platformId: String =
            requireNotNull(
                replacement
                    ?.installed
                    ?.manifest
                    ?.platform
                    ?.id
                    ?: previous
                        ?.installed
                        ?.manifest
                        ?.platform
                        ?.id
                    ?: subsystem.stateStore.desired.value.plugins[pluginId]
                        ?.manifest
                        ?.platform
                        ?.id,
            ),
    ) {
        val spec = replacement?.let(::platform)
        var retired = false
        try {
            withContext(NonCancellable) {
                previous?.let { subsystem.runtimePool.retire(it, protectInteractions = !temporary) }
                retired = true
                subsystem.oauth.cancelPlugin(pluginId)
                if (!temporary && installed == null) {
                    accounts.deleteAccounts(platformId) { json ->
                        val snapshot =
                            try {
                                PluginJsonV1.decodeFromString<PluginAccountCredentialV1>(json).snapshot
                            } catch (_: SerializationException) {
                                error("Cannot verify account ownership. Remove the affected account before deleting this plugin.")
                            }
                        snapshot.pluginId == pluginId && snapshot.platformId == platformId
                    }
                } else {
                    accounts.invalidatePlatform(platformId)
                }
                // A published replacement can immediately start writes that prevent rollback.
                if (!temporary) {
                    if (installed == null) subsystem.stateStore.uninstall(pluginId) else subsystem.stateStore.install(installed)
                }
                subsystem.replaceRunning(pluginId, replacement)
                registry.replaceExternal(platformId, spec)
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                replacement?.session?.retire()
                if (retired || previous?.session?.isActive == false) {
                    val generation = maxOf(previous?.generation ?: 0, replacement?.generation ?: 0) + 1
                    val restored =
                        previous
                            ?.copy(
                                installed = if (temporary) previous.installed else previous.installed.copy(runtimeGeneration = generation),
                                generation = generation,
                            )?.also { it.diagnostic = previous.diagnostic }
                    subsystem.replaceRunning(pluginId, restored)
                    registry.replaceExternal(platformId, restored?.let(::platform))
                    // Preserve the restored session's identity for OAuth continuations after a restart.
                    if (!temporary && restored != null) {
                        val record = restored.installed.copy(runtimeGeneration = restored.generation)
                        runCatching {
                            subsystem.stateStore.install(record)
                        }
                    }
                }
            }
            throw error
        }
    }

    private fun platform(plugin: RunningPluginV1) =
        PluginPlatformSpecSourceV1(
            running = PluginRunningSnapshotV1(mapOf(plugin.installed.pluginId to plugin), emptySet(), emptyList(), true),
            runtimePool = subsystem.runtimePool,
            oauth = subsystem.oauth,
            form = subsystem.form,
            webCookie = subsystem.webCookie,
            coroutineScope = scope,
        ).load(
            registry.all.filterNot { it.platformId == plugin.installed.manifest.platform.id }.mapTo(mutableSetOf()) { it.platformId },
        ).single()
}
