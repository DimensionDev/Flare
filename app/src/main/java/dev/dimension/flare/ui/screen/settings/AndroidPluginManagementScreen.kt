package dev.dimension.flare.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningTypeV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningV1
import dev.dimension.flare.feature.plugin.installer.PluginSensitivePermissionV1
import dev.dimension.flare.feature.plugin.management.PluginInstallReviewV1
import dev.dimension.flare.feature.plugin.management.PluginManagementItemV1
import dev.dimension.flare.feature.plugin.management.PluginManagementPresenterV1
import dev.dimension.flare.feature.plugin.management.PluginManagementStateV1
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.resolveText
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.buffer
import okio.source
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AndroidPluginManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subsystem = koinGet<PluginSubsystemV1>()
    val presenter = remember(subsystem, scope) { PluginManagementPresenterV1(subsystem, scope) }
    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }
    val state by presenter.state.collectAsState()
    var showsMaintenanceMenu by remember { mutableStateOf(false) }
    var pendingUninstallId by remember { mutableStateOf<String?>(null) }
    val pendingUninstall = state.plugins.firstOrNull { it.pluginId == pendingUninstallId }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launchPresenterAction {
                val source =
                    context.contentResolver
                        .openInputStream(uri)
                        ?.source()
                        ?.buffer()
                if (source == null) {
                    presenter.reportOperationFailure()
                } else {
                    source.use { presenter.inspect(it) }
                }
            }
        }

    state.pendingInstall?.let { review ->
        PluginInstallReviewDialog(
            review = review,
            busy = state.busy,
            onConfirm = { scope.launchPresenterAction { presenter.confirmInstall() } },
            onCancel = { scope.launchPresenterAction { presenter.cancelInstall() } },
        )
    }
    if (pendingUninstall != null) {
        AlertDialog(
            onDismissRequest = { pendingUninstallId = null },
            title = { Text(stringResource(R.string.plugin_uninstall_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.plugin_uninstall_confirm_message,
                        pendingUninstall.nameText.resolveText(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = {
                        val pluginId = pendingUninstall.pluginId
                        pendingUninstallId = null
                        scope.launchPresenterAction { presenter.uninstall(pluginId) }
                    },
                ) {
                    Text(stringResource(R.string.plugin_uninstall), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstallId = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        FlareScaffold(
            topBar = {
                FlareLargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.settings_plugins_title)) },
                    navigationIcon = { BackButton(onBack = onBack) },
                    actions = {
                        IconButton(
                            enabled = !state.busy,
                            onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Plus,
                                contentDescription = stringResource(R.string.plugin_install),
                            )
                        }
                        Box {
                            IconButton(
                                enabled = !state.busy,
                                onClick = { showsMaintenanceMenu = true },
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                    contentDescription = stringResource(R.string.more),
                                )
                            }
                            DropdownMenu(
                                expanded = showsMaintenanceMenu,
                                onDismissRequest = { showsMaintenanceMenu = false },
                            ) {
                                if (state.canRebuildIndex) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.plugin_rebuild_index)) },
                                        onClick = {
                                            showsMaintenanceMenu = false
                                            scope.launchPresenterAction { presenter.rebuildIndex() }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.plugin_cleanup)) },
                                    enabled = !state.canRebuildIndex,
                                    onClick = {
                                        showsMaintenanceMenu = false
                                        scope.launchPresenterAction { presenter.cleanup() }
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        ) { padding ->
            PluginManagementList(
                state = state,
                onSetEnabled = { pluginId, enabled ->
                    scope.launchPresenterAction { presenter.setEnabled(pluginId, enabled) }
                },
                onUninstall = { pendingUninstallId = it },
                onRetryRuntime = { pluginId ->
                    scope.launchPresenterAction { presenter.retryRuntime(pluginId) }
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = screenHorizontalPadding),
            )
        }
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("${review.name} ${review.version}") },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.plugin_unverified_warning),
                    color = MaterialTheme.colorScheme.error,
                )
                if (review.capabilities.isNotEmpty()) {
                    Text(stringResource(R.string.plugin_capabilities), style = MaterialTheme.typography.titleSmall)
                    review.capabilities.forEach { Text("• $it") }
                }
                if (review.permissions.isNotEmpty()) {
                    Text(stringResource(R.string.plugin_permissions), style = MaterialTheme.typography.titleSmall)
                    review.permissions.forEach { permission ->
                        Text("• ${permission.localizedDescription()}")
                    }
                }
                review.warnings
                    .filterNot { it.type == PluginInstallWarningTypeV1.UnverifiedLocal }
                    .forEach { Text("• ${it.localizedMessage()}") }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(stringResource(R.string.plugin_confirm_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text(stringResource(android.R.string.cancel))
            }
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
            stringResource(R.string.plugin_permission_account_origin_label) + accountRelativePath
        } else {
            origin
        }
    return cookieName?.let {
        stringResource(R.string.plugin_permission_cookie, displayOrigin, it)
    } ?: if (accountRelativePath != null) {
        stringResource(R.string.plugin_permission_account_origin)
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
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        if (state.requiresRestart) {
            item(key = "restart") {
                MessageItem(
                    title = stringResource(R.string.plugin_restart_required),
                    isError = false,
                )
            }
        }
        if (state.error != null) {
            item(key = "operation-error") {
                MessageItem(
                    title = stringResource(R.string.plugin_operation_failed),
                    isError = true,
                )
            }
        }
        state.issues.forEach { issue ->
            item(key = "issue:${issue.code}:${issue.pluginId}") {
                MessageItem(
                    title = pluginIssueMessage(issue.code),
                    isError = true,
                )
            }
        }
        state.runtimeIssues.forEach { issue ->
            item(key = "runtime:${issue.pluginId}") {
                SegmentedListItem(
                    onClick = {},
                    shapes = ListItemDefaults.segmentedShapes2(0, 1),
                    content = { Text(runtimeIssueMessage(issue.code)) },
                    supportingContent = { Text(issue.pluginId) },
                    trailingContent = {
                        TextButton(
                            enabled = !state.busy,
                            onClick = { onRetryRuntime(issue.pluginId) },
                        ) {
                            Text(stringResource(R.string.plugin_retry))
                        }
                    },
                )
            }
        }
        if (state.plugins.isEmpty()) {
            item(key = "empty") {
                SegmentedListItem(
                    onClick = {},
                    shapes = ListItemDefaults.segmentedShapes2(0, 1),
                    content = { Text(stringResource(R.string.plugin_empty)) },
                    supportingContent = { Text(stringResource(R.string.settings_plugins_subtitle)) },
                )
            }
        } else {
            itemsIndexed(
                items = state.plugins,
                key = { _, plugin -> plugin.pluginId },
            ) { index, plugin ->
                PluginItem(
                    plugin = plugin,
                    busy = state.busy,
                    shapesIndex = index,
                    shapesCount = state.plugins.size,
                    onSetEnabled = onSetEnabled,
                    onUninstall = onUninstall,
                )
            }
        }
    }
}

@Composable
private fun PluginItem(
    plugin: PluginManagementItemV1,
    busy: Boolean,
    shapesIndex: Int,
    shapesCount: Int,
    onSetEnabled: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes2(shapesIndex, shapesCount),
        leadingContent = {
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
        content = { Text(plugin.nameText.resolveText()) },
        supportingContent = {
            Column {
                Text("${plugin.platformId} · ${plugin.version}")
                Text(
                    if (plugin.running) {
                        stringResource(R.string.plugin_running)
                    } else {
                        stringResource(R.string.plugin_inactive)
                    },
                )
                if (plugin.pendingRestart) {
                    Text(
                        stringResource(R.string.plugin_restart_required),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = plugin.enabled,
                    enabled = !busy,
                    onCheckedChange = { onSetEnabled(plugin.pluginId, it) },
                )
                IconButton(
                    enabled = !busy,
                    onClick = { onUninstall(plugin.pluginId) },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Trash,
                        contentDescription = stringResource(R.string.plugin_uninstall),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun MessageItem(
    title: String,
    isError: Boolean,
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes2(0, 1),
        content = {
            Text(
                title,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun pluginIssueMessage(code: String): String =
    stringResource(
        when (code) {
            "index.corrupt" -> R.string.plugin_issue_index_corrupt
            "package.missing-or-changed", "icon.missing-or-changed" -> R.string.plugin_issue_files_missing
            "platform.conflict" -> R.string.plugin_issue_platform_conflict
            "platform.invalid" -> R.string.plugin_issue_platform_invalid
            else -> R.string.plugin_operation_failed
        },
    )

@Composable
private fun runtimeIssueMessage(code: String): String =
    stringResource(
        if (code == "runtime.paused") {
            R.string.plugin_runtime_paused
        } else {
            R.string.plugin_runtime_fatal
        },
    )

@Composable
private fun PluginInstallWarningV1.localizedMessage(): String {
    val prefix =
        stringResource(
            when (type) {
                PluginInstallWarningTypeV1.UnverifiedLocal -> R.string.plugin_unverified_warning
                PluginInstallWarningTypeV1.AddedPermission -> R.string.plugin_warning_added_permission
                PluginInstallWarningTypeV1.Downgrade -> R.string.plugin_warning_downgrade
                PluginInstallWarningTypeV1.SameVersionDifferentHash -> R.string.plugin_warning_changed_package
                PluginInstallWarningTypeV1.Compatibility -> R.string.plugin_warning_compatibility
            },
        )
    val localizedDetail =
        detail?.replace(
            PluginAbiV1.ACCOUNT_ORIGIN,
            stringResource(R.string.plugin_permission_account_origin_label),
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
