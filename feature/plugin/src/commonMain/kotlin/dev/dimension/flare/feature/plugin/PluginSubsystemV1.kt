package dev.dimension.flare.feature.plugin

import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.feature.plugin.adapter.PluginPlatformSpecSourceV1
import dev.dimension.flare.feature.plugin.host.createKtorPluginHttpTransport
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateIssueV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.login.PlatformOAuthPluginPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginFormLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginWebCookieLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.model.PlatformSpecSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.native.HiddenFromObjC

/** Process-scoped plugin services. Opening this object reads only the bounded state index. */
@HiddenFromObjC
public class PluginSubsystemV1(
    appFiles: AppFileStore,
    pendingRepository: PlatformOAuthPendingRepository,
) {
    public val stateStore: PluginStateStore =
        PluginStateStore.open(
            fileSystem = appFiles.fileSystem,
            root = appFiles.directory(STORAGE_NAMESPACE),
        )
    public val runtimePool: PluginRuntimePool =
        PluginRuntimePool(
            fileSystem = appFiles.fileSystem,
            transportFactory = ::createKtorPluginHttpTransport,
        )
    public val installer: PluginInstaller = PluginInstaller(appFiles.fileSystem, stateStore)

    private val mutableSourceIssues = MutableStateFlow(stateStore.running.issues)
    public val sourceIssues: StateFlow<List<PluginStateIssueV1>> = mutableSourceIssues.asStateFlow()

    private val pendingStore = PlatformOAuthPluginPendingStoreV1(pendingRepository)
    public val oauth: PluginOAuthLoginCoordinatorV1 =
        PluginOAuthLoginCoordinatorV1(
            runtimePool = runtimePool,
            pendingStore = pendingStore,
            runningPlugin = { pluginId -> stateStore.running.plugins[pluginId] },
        )
    public val form: PluginFormLoginCoordinatorV1 = PluginFormLoginCoordinatorV1(runtimePool)
    public val webCookie: PluginWebCookieLoginCoordinatorV1 = PluginWebCookieLoginCoordinatorV1(runtimePool)

    public fun platformSource(scope: CoroutineScope): PlatformSpecSource =
        PluginPlatformSpecSourceV1(
            running = stateStore.running,
            runtimePool = runtimePool,
            oauth = oauth,
            form = form,
            webCookie = webCookie,
            coroutineScope = scope,
            onIssue = { issue -> mutableSourceIssues.value = (mutableSourceIssues.value + issue).distinct() },
        )

    public suspend fun onMemoryPressure() {
        runtimePool.closeIdle()
    }

    private companion object {
        const val STORAGE_NAMESPACE = "social-plugins-v3"
    }
}
