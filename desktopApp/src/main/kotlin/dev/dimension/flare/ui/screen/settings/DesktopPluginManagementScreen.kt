package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.management.PluginManagementPresenterV1
import dev.dimension.flare.feature.plugin.management.PluginManagementScreen
import dev.dimension.flare.feature.plugin.management.PluginManagementStringsV1
import dev.dimension.flare.ok
import dev.dimension.flare.plugin_capabilities
import dev.dimension.flare.plugin_cleanup
import dev.dimension.flare.plugin_empty
import dev.dimension.flare.plugin_inactive
import dev.dimension.flare.plugin_install
import dev.dimension.flare.plugin_permissions
import dev.dimension.flare.plugin_rebuild_index
import dev.dimension.flare.plugin_restart_required
import dev.dimension.flare.plugin_retry
import dev.dimension.flare.plugin_running
import dev.dimension.flare.plugin_uninstall
import dev.dimension.flare.plugin_unverified_warning
import dev.dimension.flare.plugin_warning_added_permission
import dev.dimension.flare.plugin_warning_changed_package
import dev.dimension.flare.plugin_warning_compatibility
import dev.dimension.flare.plugin_warning_downgrade
import dev.dimension.flare.settings_plugins_title
import dev.dimension.flare.ui.theme.LocalComposeWindow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.io.File

@Composable
internal fun DesktopPluginManagementScreen(onBack: () -> Unit) {
    val window = LocalComposeWindow.current
    val subsystem = koinInject<PluginSubsystemV1>()
    val scope = rememberCoroutineScope()
    val presenter = remember(subsystem, scope) { PluginManagementPresenterV1(subsystem, scope) }
    val state by presenter.state.collectAsState()

    PluginManagementScreen(
        state = state,
        strings =
            PluginManagementStringsV1(
                title = stringResource(Res.string.settings_plugins_title),
                install = stringResource(Res.string.plugin_install),
                empty = stringResource(Res.string.plugin_empty),
                restartRequired = stringResource(Res.string.plugin_restart_required),
                unverifiedWarning = stringResource(Res.string.plugin_unverified_warning),
                permissions = stringResource(Res.string.plugin_permissions),
                capabilities = stringResource(Res.string.plugin_capabilities),
                confirm = stringResource(Res.string.ok),
                cancel = stringResource(Res.string.cancel),
                uninstall = stringResource(Res.string.plugin_uninstall),
                cleanup = stringResource(Res.string.plugin_cleanup),
                rebuildIndex = stringResource(Res.string.plugin_rebuild_index),
                retry = stringResource(Res.string.plugin_retry),
                running = stringResource(Res.string.plugin_running),
                inactive = stringResource(Res.string.plugin_inactive),
                addedPermissionWarning = stringResource(Res.string.plugin_warning_added_permission),
                downgradeWarning = stringResource(Res.string.plugin_warning_downgrade),
                changedPackageWarning = stringResource(Res.string.plugin_warning_changed_package),
                compatibilityWarning = stringResource(Res.string.plugin_warning_compatibility),
            ),
        onBack = onBack,
        onInstall = {
            FileDialog(window, "Install Flare plugin", FileDialog.LOAD).apply {
                file = "*.fpp"
                isVisible = true
                if (directory != null && file != null) {
                    val path = File(directory, file).absolutePath
                    scope.launch { runCatching { presenter.inspect(path) } }
                }
            }
        },
        onConfirmInstall = { scope.launch { runCatching { presenter.confirmInstall() } } },
        onCancelInstall = { scope.launch { runCatching { presenter.cancelInstall() } } },
        onSetEnabled = { id, enabled -> scope.launch { runCatching { presenter.setEnabled(id, enabled) } } },
        onUninstall = { id -> scope.launch { runCatching { presenter.uninstall(id) } } },
        onRetryRuntime = { id -> scope.launch { runCatching { presenter.retryRuntime(id) } } },
        onCleanup = { scope.launch { runCatching { presenter.cleanup() } } },
        onRebuildIndex = { scope.launch { runCatching { presenter.rebuildIndex() } } },
    )
}
