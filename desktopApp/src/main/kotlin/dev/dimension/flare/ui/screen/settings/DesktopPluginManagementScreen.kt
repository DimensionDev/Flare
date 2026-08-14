package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningTypeV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningV1
import dev.dimension.flare.feature.plugin.installer.PluginSensitivePermissionV1
import dev.dimension.flare.feature.plugin.management.PluginInstallReviewV1
import dev.dimension.flare.feature.plugin.management.PluginManagementItemV1
import dev.dimension.flare.feature.plugin.management.PluginManagementPresenterV1
import dev.dimension.flare.feature.plugin.management.PluginManagementStateV1
import dev.dimension.flare.plugin_capabilities
import dev.dimension.flare.plugin_cleanup
import dev.dimension.flare.plugin_confirm_install
import dev.dimension.flare.plugin_empty
import dev.dimension.flare.plugin_inactive
import dev.dimension.flare.plugin_install
import dev.dimension.flare.plugin_issue_files_missing
import dev.dimension.flare.plugin_issue_index_corrupt
import dev.dimension.flare.plugin_issue_platform_conflict
import dev.dimension.flare.plugin_issue_platform_invalid
import dev.dimension.flare.plugin_issues
import dev.dimension.flare.plugin_operation_failed
import dev.dimension.flare.plugin_permission_account_origin
import dev.dimension.flare.plugin_permission_account_origin_label
import dev.dimension.flare.plugin_permission_cookie
import dev.dimension.flare.plugin_permissions
import dev.dimension.flare.plugin_rebuild_index
import dev.dimension.flare.plugin_restart_required
import dev.dimension.flare.plugin_retry
import dev.dimension.flare.plugin_running
import dev.dimension.flare.plugin_runtime_fatal
import dev.dimension.flare.plugin_runtime_paused
import dev.dimension.flare.plugin_uninstall
import dev.dimension.flare.plugin_uninstall_confirm_message
import dev.dimension.flare.plugin_uninstall_confirm_title
import dev.dimension.flare.plugin_unverified_warning
import dev.dimension.flare.plugin_warning_added_permission
import dev.dimension.flare.plugin_warning_changed_package
import dev.dimension.flare.plugin_warning_compatibility
import dev.dimension.flare.plugin_warning_downgrade
import dev.dimension.flare.settings_plugins_subtitle
import dev.dimension.flare.settings_plugins_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.resolveText
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.InfoBar
import io.github.composefluent.component.InfoBarSeverity
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }
    val state by presenter.state.collectAsState()
    val installLabel = stringResource(Res.string.plugin_install)
    var showsMaintenanceMenu by remember { mutableStateOf(false) }
    var pendingUninstallId by remember { mutableStateOf<String?>(null) }
    val pendingUninstall = state.plugins.firstOrNull { it.pluginId == pendingUninstallId }

    state.pendingInstall?.let { review ->
        PluginInstallReviewDialog(
            review = review,
            busy = state.busy,
            onConfirm = { scope.launchPresenterAction { presenter.confirmInstall() } },
            onCancel = { scope.launchPresenterAction { presenter.cancelInstall() } },
        )
    }
    ContentDialog(
        title = stringResource(Res.string.plugin_uninstall_confirm_title),
        visible = pendingUninstall != null,
        content = {
            Text(
                stringResource(
                    Res.string.plugin_uninstall_confirm_message,
                    pendingUninstall?.nameText?.resolveText().orEmpty(),
                ),
            )
        },
        primaryButtonText = stringResource(Res.string.plugin_uninstall),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = { button ->
            if (button == ContentDialogButton.Primary) {
                pendingUninstall?.pluginId?.let { pluginId ->
                    scope.launchPresenterAction { presenter.uninstall(pluginId) }
                }
            }
            pendingUninstallId = null
        },
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(LocalWindowPadding.current),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtleButton(onClick = onBack, iconOnly = true) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ArrowLeft,
                        contentDescription = null,
                    )
                }
                Text(
                    text = stringResource(Res.string.settings_plugins_title),
                    style = FluentTheme.typography.title,
                )
                Box(Modifier.weight(1f))
                Box {
                    SubtleButton(
                        onClick = { showsMaintenanceMenu = true },
                        disabled = state.busy,
                        iconOnly = true,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                            contentDescription = null,
                        )
                    }
                    MenuFlyout(
                        visible = showsMaintenanceMenu,
                        onDismissRequest = { showsMaintenanceMenu = false },
                        placement = FlyoutPlacement.BottomAlignedEnd,
                    ) {
                        if (state.canRebuildIndex) {
                            MenuFlyoutItem(
                                text = { Text(stringResource(Res.string.plugin_rebuild_index)) },
                                onClick = {
                                    showsMaintenanceMenu = false
                                    scope.launchPresenterAction { presenter.rebuildIndex() }
                                },
                            )
                        }
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.plugin_cleanup)) },
                            enabled = !state.canRebuildIndex,
                            onClick = {
                                showsMaintenanceMenu = false
                                scope.launchPresenterAction { presenter.cleanup() }
                            },
                        )
                    }
                }
                AccentButton(
                    disabled = state.busy,
                    onClick = {
                        FileDialog(window, installLabel, FileDialog.LOAD).apply {
                            file = "*.fpp"
                            isVisible = true
                            if (directory != null && file != null) {
                                val path = File(directory, file).absolutePath
                                scope.launchPresenterAction { presenter.inspect(path) }
                            }
                        }
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Plus,
                        contentDescription = installLabel,
                    )
                    Text(installLabel)
                }
            }
            PluginManagementList(
                state = state,
                onSetEnabled = { pluginId, enabled ->
                    scope.launchPresenterAction { presenter.setEnabled(pluginId, enabled) }
                },
                onUninstall = { pendingUninstallId = it },
                onRetryRuntime = { pluginId ->
                    scope.launchPresenterAction { presenter.retryRuntime(pluginId) }
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (state.busy) {
            ProgressRing(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun PluginInstallReviewDialog(
    review: PluginInstallReviewV1,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    ContentDialog(
        title = "${review.name} ${review.version}",
        visible = true,
        content = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(Res.string.plugin_unverified_warning))
                if (review.capabilities.isNotEmpty()) {
                    Text(stringResource(Res.string.plugin_capabilities), style = FluentTheme.typography.bodyStrong)
                    review.capabilities.forEach { Text("• $it") }
                }
                if (review.permissions.isNotEmpty()) {
                    Text(stringResource(Res.string.plugin_permissions), style = FluentTheme.typography.bodyStrong)
                    review.permissions.forEach { permission ->
                        Text("• ${permission.localizedDescription()}")
                    }
                }
                review.warnings
                    .filterNot { it.type == PluginInstallWarningTypeV1.UnverifiedLocal }
                    .forEach { Text("• ${it.localizedMessage()}") }
            }
        },
        primaryButtonText = stringResource(Res.string.plugin_confirm_install),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = { button ->
            if (busy) return@ContentDialog
            if (button == ContentDialogButton.Primary) onConfirm() else onCancel()
        },
    )
}

@Composable
private fun PluginSensitivePermissionV1.localizedDescription(): String {
    val accountRelativePath =
        origin
            .takeIf { it == PluginAbiV1.ACCOUNT_ORIGIN || it.startsWith("${PluginAbiV1.ACCOUNT_ORIGIN}/") }
            ?.removePrefix(PluginAbiV1.ACCOUNT_ORIGIN)
    val displayOrigin =
        if (accountRelativePath != null) {
            stringResource(Res.string.plugin_permission_account_origin_label) + accountRelativePath
        } else {
            origin
        }
    return cookieName?.let {
        stringResource(Res.string.plugin_permission_cookie, displayOrigin, it)
    } ?: if (accountRelativePath != null) {
        stringResource(Res.string.plugin_permission_account_origin)
    } else {
        displayOrigin
    }
}

@Composable
private fun PluginManagementList(
    state: PluginManagementStateV1,
    onSetEnabled: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
    onRetryRuntime: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    FlareScrollBar(state = listState, modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.requiresRestart) {
                item(key = "restart") {
                    InfoBar(
                        title = { Text(stringResource(Res.string.plugin_restart_required)) },
                        message = {},
                        severity = InfoBarSeverity.Warning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (state.error != null) {
                item(key = "operation-error") {
                    ErrorInfoBar(stringResource(Res.string.plugin_operation_failed))
                }
            }
            state.issues.forEach { issue ->
                item(key = "issue:${issue.code}:${issue.pluginId}") {
                    ErrorInfoBar(pluginIssueMessage(issue.code))
                }
            }
            state.runtimeIssues.forEach { issue ->
                item(key = "runtime:${issue.pluginId}") {
                    InfoBar(
                        title = { Text(runtimeIssueMessage(issue.code)) },
                        message = { Text(issue.pluginId) },
                        severity = InfoBarSeverity.Critical,
                        action = {
                            SubtleButton(
                                disabled = state.busy,
                                onClick = { onRetryRuntime(issue.pluginId) },
                            ) {
                                Text(stringResource(Res.string.plugin_retry))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (state.plugins.isEmpty()) {
                item(key = "empty") {
                    CardExpanderItem(
                        heading = { Text(stringResource(Res.string.plugin_empty)) },
                        caption = { Text(stringResource(Res.string.settings_plugins_subtitle)) },
                        icon = null,
                    )
                }
            } else {
                items(state.plugins, key = PluginManagementItemV1::pluginId) { plugin ->
                    PluginItem(
                        plugin = plugin,
                        busy = state.busy,
                        onSetEnabled = onSetEnabled,
                        onUninstall = onUninstall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginItem(
    plugin: PluginManagementItemV1,
    busy: Boolean,
    onSetEnabled: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
) {
    CardExpanderItem(
        heading = { Text(plugin.nameText.resolveText()) },
        icon = {
            AsyncImage(
                model = File(plugin.iconPath),
                contentDescription = plugin.name,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )
        },
        caption = {
            Column {
                Text("${plugin.platformId} · ${plugin.version}")
                Text(
                    if (plugin.running) {
                        stringResource(Res.string.plugin_running)
                    } else {
                        stringResource(Res.string.plugin_inactive)
                    },
                )
                if (plugin.pendingRestart) {
                    Text(stringResource(Res.string.plugin_restart_required))
                }
            }
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Switcher(
                    checked = plugin.enabled,
                    enabled = !busy,
                    onCheckStateChange = { onSetEnabled(plugin.pluginId, it) },
                )
                SubtleButton(
                    disabled = busy,
                    iconOnly = true,
                    onClick = { onUninstall(plugin.pluginId) },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Trash,
                        contentDescription = stringResource(Res.string.plugin_uninstall),
                    )
                }
            }
        },
    )
}

@Composable
private fun ErrorInfoBar(message: String) {
    InfoBar(
        title = { Text(stringResource(Res.string.plugin_issues)) },
        message = { Text(message) },
        severity = InfoBarSeverity.Critical,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun pluginIssueMessage(code: String): String =
    stringResource(
        when (code) {
            "index.corrupt" -> Res.string.plugin_issue_index_corrupt
            "package.missing-or-changed", "icon.missing-or-changed" -> Res.string.plugin_issue_files_missing
            "platform.conflict" -> Res.string.plugin_issue_platform_conflict
            "platform.invalid" -> Res.string.plugin_issue_platform_invalid
            else -> Res.string.plugin_operation_failed
        },
    )

@Composable
private fun runtimeIssueMessage(code: String): String =
    stringResource(
        if (code == "runtime.paused") {
            Res.string.plugin_runtime_paused
        } else {
            Res.string.plugin_runtime_fatal
        },
    )

@Composable
private fun PluginInstallWarningV1.localizedMessage(): String {
    val prefix =
        stringResource(
            when (type) {
                PluginInstallWarningTypeV1.UnverifiedLocal -> Res.string.plugin_unverified_warning
                PluginInstallWarningTypeV1.AddedPermission -> Res.string.plugin_warning_added_permission
                PluginInstallWarningTypeV1.Downgrade -> Res.string.plugin_warning_downgrade
                PluginInstallWarningTypeV1.SameVersionDifferentHash -> Res.string.plugin_warning_changed_package
                PluginInstallWarningTypeV1.Compatibility -> Res.string.plugin_warning_compatibility
            },
        )
    val localizedDetail =
        detail?.replace(
            PluginAbiV1.ACCOUNT_ORIGIN,
            stringResource(Res.string.plugin_permission_account_origin_label),
        )
    return localizedDetail?.let { "$prefix: $it" } ?: prefix
}

private fun CoroutineScope.launchPresenterAction(action: suspend () -> Unit) {
    launch {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The presenter publishes a localized-safe error state.
        }
    }
}
