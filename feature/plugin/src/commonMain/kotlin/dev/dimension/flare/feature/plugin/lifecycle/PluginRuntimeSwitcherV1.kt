package dev.dimension.flare.feature.plugin.lifecycle

import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.adapter.PluginPlatformSpecSourceV1
import dev.dimension.flare.model.PlatformRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Caller holds the subsystem mutation lock; failed replacements restore fresh sessions. */
internal class PluginRuntimeSwitcherV1(
    private val subsystem: PluginSubsystemV1,
    private val registry: PlatformRegistry,
    private val accounts: AccountService,
    private val scope: CoroutineScope,
) {
    suspend fun switch(
        previous: RunningPluginV1?,
        replacement: RunningPluginV1?,
        installed: InstalledPluginV1? = replacement?.installed,
        pluginId: String =
            requireNotNull(installed?.pluginId ?: previous?.installed?.pluginId),
        temporary: Boolean = false,
    ) {
        val before = subsystem.stateStore.desired.value.plugins[pluginId]
        val platformId =
            requireNotNull(
                replacement
                    ?.installed
                    ?.manifest
                    ?.platform
                    ?.id ?: previous
                    ?.installed
                    ?.manifest
                    ?.platform
                    ?.id ?: before?.manifest?.platform?.id,
            )
        val spec = replacement?.let(::platform)
        var retired = false
        try {
            withContext(NonCancellable) {
                previous?.let { subsystem.runtimePool.retire(it, protectInteractions = !temporary) }
                retired = true
                subsystem.oauth.cancelPlugin(pluginId)
                accounts.invalidatePlatform(platformId)
                // A published replacement can immediately start writes that prevent rollback.
                if (!temporary) {
                    if (installed == null) subsystem.stateStore.uninstall(pluginId) else subsystem.stateStore.install(installed)
                }
                subsystem.replaceRunning(pluginId, replacement)
                registry.replaceExternal(platformId, spec)
                if (!temporary) subsystem.stateStore.markApplied(pluginId, installed)
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
                            subsystem.stateStore.markApplied(pluginId, record)
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
