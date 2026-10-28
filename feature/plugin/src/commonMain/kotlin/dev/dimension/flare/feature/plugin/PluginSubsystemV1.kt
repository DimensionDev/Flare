package dev.dimension.flare.feature.plugin

import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.adapter.PluginPlatformSpecSourceV1
import dev.dimension.flare.feature.plugin.authoring.PluginAuthoringServiceV1
import dev.dimension.flare.feature.plugin.development.PluginDevelopmentSessionV1
import dev.dimension.flare.feature.plugin.development.PluginDevelopmentStateV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginClient
import dev.dimension.flare.feature.plugin.host.createKtorPluginHttpTransport
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateIssueV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.derivedPluginState
import dev.dimension.flare.feature.plugin.login.PlatformOAuthPluginPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginFormLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginWebCookieLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformSpecSource
import dev.dimension.flare.ui.model.PluginMediaPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.native.HiddenFromObjC

/** Process-scoped plugin services. Opening this object reads only the bounded state index. */
@HiddenFromObjC
public class PluginSubsystemV1 internal constructor(
    internal val appFiles: AppFileStore,
    pendingStore: PluginOAuthPendingStoreV1,
) {
    internal val mutationMutex = Mutex()

    public constructor(
        appFiles: AppFileStore,
        pendingRepository: PlatformOAuthPendingRepository,
    ) : this(appFiles, PlatformOAuthPluginPendingStoreV1(pendingRepository))

    public val stateStore: PluginStateStore =
        PluginStateStore
            .open(
                fileSystem = appFiles.fileSystem,
                root = appFiles.directory(STORAGE_NAMESPACE),
            ).also { store ->
                PluginMediaPermissions.replace(store.running.plugins.mapValues { it.value.installed.manifest.permissions.mediaNetwork })
            }
    public val runtimePool: PluginRuntimePool =
        PluginRuntimePool(
            fileSystem = appFiles.fileSystem,
            transportFactory = ::createKtorPluginHttpTransport,
        )
    public val installer: PluginInstaller = PluginInstaller(appFiles.fileSystem, stateStore)
    public val github: GitHubPluginClient by lazy { GitHubPluginClient() }

    private val mutableRunning = MutableStateFlow(stateStore.running)
    public val running: StateFlow<dev.dimension.flare.feature.plugin.lifecycle.PluginRunningSnapshotV1> = mutableRunning.asStateFlow()
    internal val mutableDevelopmentState = MutableStateFlow(PluginDevelopmentStateV1())
    public val developmentState: StateFlow<PluginDevelopmentStateV1> =
        derivedPluginState(mutableDevelopmentState, running) { state, running ->
            val active = running.plugins[state.pluginId]?.takeIf { it.developmentSource != null }
            state.copy(
                active = active != null,
                runningHash = active?.installed?.packageHash,
                generation = active?.generation ?: 0,
            )
        }
    public val development: PluginDevelopmentSessionV1 by lazy {
        PluginDevelopmentSessionV1(this, koinGet<PlatformRegistry>(), koinGet<AccountService>(), koinGet<CoroutineScope>())
    }

    internal val authoring: PluginAuthoringServiceV1 by lazy {
        PluginAuthoringServiceV1(this, { koinGet<PlatformRegistry>() }, { koinGet<AccountService>() }, { koinGet<CoroutineScope>() })
    }

    internal fun replaceRunning(
        pluginId: String,
        plugin: RunningPluginV1?,
    ) {
        val plugins = mutableRunning.value.plugins.toMutableMap()
        if (plugin == null) plugins.remove(pluginId) else plugins[pluginId] = plugin
        PluginMediaPermissions.register(
            pluginId,
            plugin
                ?.installed
                ?.manifest
                ?.permissions
                ?.mediaNetwork == true,
        )
        mutableRunning.value = mutableRunning.value.copy(plugins = plugins)
    }

    private val mutableSourceIssues = MutableStateFlow(stateStore.running.issues)
    public val sourceIssues: StateFlow<List<PluginStateIssueV1>> = mutableSourceIssues.asStateFlow()

    public val oauth: PluginOAuthLoginCoordinatorV1 =
        PluginOAuthLoginCoordinatorV1(
            runtimePool = runtimePool,
            pendingStore = pendingStore,
            runningPlugin = { pluginId -> running.value.plugins[pluginId] },
        )
    public val form: PluginFormLoginCoordinatorV1 = PluginFormLoginCoordinatorV1(runtimePool)
    public val webCookie: PluginWebCookieLoginCoordinatorV1 = PluginWebCookieLoginCoordinatorV1(runtimePool)

    public fun platformSource(scope: CoroutineScope): PlatformSpecSource =
        PluginPlatformSpecSourceV1(
            running = running.value,
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
