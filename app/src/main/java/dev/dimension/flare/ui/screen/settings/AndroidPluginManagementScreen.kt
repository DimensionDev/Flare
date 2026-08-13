package dev.dimension.flare.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.management.PluginManagementPresenterV1
import dev.dimension.flare.feature.plugin.management.PluginManagementScreen
import dev.dimension.flare.feature.plugin.management.PluginManagementStringsV1
import kotlinx.coroutines.launch
import okio.buffer
import okio.source

@Composable
internal fun AndroidPluginManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presenter = remember { PluginManagementPresenterV1(koinGet<PluginSubsystemV1>(), scope) }
    val state by presenter.state.collectAsState()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    context.contentResolver
                        .openInputStream(uri)
                        ?.source()
                        ?.buffer()
                        ?.use { presenter.inspect(it) }
                        ?: error("Unable to open plugin package")
                }
            }
        }

    PluginManagementScreen(
        state = state,
        strings = pluginManagementStrings(),
        onBack = onBack,
        onInstall = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
        onConfirmInstall = { scope.launch { runCatching { presenter.confirmInstall() } } },
        onCancelInstall = { scope.launch { runCatching { presenter.cancelInstall() } } },
        onSetEnabled = { id, enabled -> scope.launch { runCatching { presenter.setEnabled(id, enabled) } } },
        onUninstall = { id -> scope.launch { runCatching { presenter.uninstall(id) } } },
        onRetryRuntime = { id -> scope.launch { runCatching { presenter.retryRuntime(id) } } },
        onCleanup = { scope.launch { runCatching { presenter.cleanup() } } },
        onRebuildIndex = { scope.launch { runCatching { presenter.rebuildIndex() } } },
    )
}

@Composable
private fun pluginManagementStrings(): PluginManagementStringsV1 =
    PluginManagementStringsV1(
        title = stringResource(R.string.settings_plugins_title),
        install = stringResource(R.string.plugin_install),
        empty = stringResource(R.string.plugin_empty),
        restartRequired = stringResource(R.string.plugin_restart_required),
        unverifiedWarning = stringResource(R.string.plugin_unverified_warning),
        permissions = stringResource(R.string.plugin_permissions),
        capabilities = stringResource(R.string.plugin_capabilities),
        confirm = stringResource(android.R.string.ok),
        cancel = stringResource(android.R.string.cancel),
        uninstall = stringResource(R.string.plugin_uninstall),
        cleanup = stringResource(R.string.plugin_cleanup),
        rebuildIndex = stringResource(R.string.plugin_rebuild_index),
        retry = stringResource(R.string.plugin_retry),
        running = stringResource(R.string.plugin_running),
        inactive = stringResource(R.string.plugin_inactive),
        addedPermissionWarning = stringResource(R.string.plugin_warning_added_permission),
        downgradeWarning = stringResource(R.string.plugin_warning_downgrade),
        changedPackageWarning = stringResource(R.string.plugin_warning_changed_package),
        compatibilityWarning = stringResource(R.string.plugin_warning_compatibility),
    )
