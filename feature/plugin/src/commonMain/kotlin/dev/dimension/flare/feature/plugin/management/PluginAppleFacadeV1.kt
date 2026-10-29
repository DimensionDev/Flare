package dev.dimension.flare.feature.plugin.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthCallbackCoordinatorV1
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope

/** Swift-facing management API; file import and confirmation UI remain native. */
public class PluginAppleFacadeV1 : PresenterBase<PluginManagementStateV1>() {
    @Throws(Exception::class)
    public suspend fun cancelPluginUpdate(pluginId: String) {
        presenter.cancelPluginUpdate(pluginId)
    }

    public fun prepareDevelopment() {
        koinGet<PluginSubsystemV1>().development
    }

    @Throws(Exception::class)
    public suspend fun openDevelopmentDirectory(path: String) {
        koinGet<PluginSubsystemV1>().development.open(path)
    }

    @Throws(Exception::class)
    public suspend fun reloadDevelopment(confirmed: Boolean) {
        koinGet<PluginSubsystemV1>().development.reload(confirmed)
    }

    public fun cancelDevelopmentReview() {
        koinGet<PluginSubsystemV1>().development.cancelReview()
    }

    @Throws(Exception::class)
    public suspend fun stopDevelopment() {
        koinGet<PluginSubsystemV1>().development.stop()
    }

    private val presenter by lazy {
        PluginManagementPresenterV1(
            subsystem = koinGet<PluginSubsystemV1>(),
            scope = koinGet<CoroutineScope>(),
        )
    }

    @Composable
    override fun body(): PluginManagementStateV1 {
        DisposableEffect(presenter) {
            onDispose { presenter.close() }
        }
        return presenter.state.collectAsState().value
    }

    @Throws(Exception::class)
    public suspend fun inspect(path: String): PluginInstallReviewV1 = presenter.inspect(path)

    public fun reportOperationFailure() {
        presenter.reportOperationFailure()
    }

    public fun clearGitHubRepository() {
        presenter.clearGitHubRepository()
    }

    @Throws(Exception::class)
    public suspend fun loadGitHubRepository(url: String) {
        presenter.loadGitHubRepository(url)
    }

    @Throws(Exception::class)
    public suspend fun inspectGitHub(tag: String) {
        presenter.inspectGitHub(tag)
    }

    @Throws(Exception::class)
    public suspend fun checkUpdates(forceRefresh: Boolean) {
        presenter.checkUpdates(forceRefresh)
    }

    @Throws(Exception::class)
    public suspend fun inspectGitHubUpdate(pluginId: String) {
        presenter.inspectGitHubUpdate(pluginId)
    }

    @Throws(Exception::class)
    public suspend fun confirmInstall() {
        presenter.confirmInstall()
    }

    @Throws(Exception::class)
    public suspend fun cancelInstall() {
        presenter.cancelInstall()
    }

    @Throws(Exception::class)
    public suspend fun uninstall(pluginId: String) {
        presenter.uninstall(pluginId)
    }

    @Throws(Exception::class)
    public suspend fun retryRuntime(pluginId: String) {
        presenter.retryRuntime(pluginId)
    }

    @Throws(Exception::class)
    public suspend fun cleanup() {
        presenter.cleanup()
    }

    @Throws(Exception::class)
    public suspend fun rebuildIndex() {
        presenter.rebuildIndex()
    }
}

/** Swift-facing entry point for OAuth callbacks delivered after the login UI or process disappeared. */
public class PluginOAuthCallbackFacadeV1 {
    private val coordinator = koinGet<PluginOAuthCallbackCoordinatorV1>()

    public fun canHandle(url: String): Boolean = coordinator.canHandle(url)

    @Throws(Exception::class)
    public suspend fun handle(url: String) {
        check(coordinator.handle(url, Locale.language)) { "Unsupported plugin OAuth callback" }
    }
}

/** Swift-facing hook for native memory-pressure notifications. */
public class PluginRuntimeLifecycleFacadeV1 {
    @Throws(Exception::class)
    public suspend fun onMemoryPressure() {
        koinGet<PluginSubsystemV1>().onMemoryPressure()
    }
}
