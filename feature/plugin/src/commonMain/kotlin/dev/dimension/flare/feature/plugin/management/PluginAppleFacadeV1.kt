package dev.dimension.flare.feature.plugin.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope

/** Swift-facing management API; file import and confirmation UI remain native. */
public class PluginAppleFacadeV1 : PresenterBase<PluginManagementStateV1>() {
    private val presenter =
        PluginManagementPresenterV1(
            subsystem = koinGet<PluginSubsystemV1>(),
            scope = koinGet<CoroutineScope>(),
        )

    @Composable
    override fun body(): PluginManagementStateV1 = presenter.state.collectAsState().value

    @Throws(Exception::class)
    public suspend fun inspect(path: String): PluginInstallReviewV1 = presenter.inspect(path)

    @Throws(Exception::class)
    public suspend fun confirmInstall() {
        presenter.confirmInstall()
    }

    @Throws(Exception::class)
    public suspend fun cancelInstall() {
        presenter.cancelInstall()
    }

    @Throws(Exception::class)
    public suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ) {
        presenter.setEnabled(pluginId, enabled)
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
