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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.model.PluginPermission
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningTypeV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningV1
import dev.dimension.flare.feature.plugin.management.PluginInstallReviewV1
import dev.dimension.flare.feature.plugin.management.PluginManagementItemV1
import dev.dimension.flare.feature.plugin.management.PluginManagementPresenterV1
import dev.dimension.flare.feature.plugin.management.PluginManagementStateV1
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalTimelineAppearance
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
internal fun AndroidPluginManagementScreen(
    onBack: () -> Unit,
    onCreateWithAI: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subsystem = koinGet<PluginSubsystemV1>()
    val presenter = remember(subsystem, scope) { PluginManagementPresenterV1(subsystem, scope) }
    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }
    val state by presenter.state.collectAsState()
    LaunchedEffect(presenter) { scope.launchPresenterAction { presenter.checkUpdates(forceRefresh = false) } }
    var showsGitHubInstall by remember { mutableStateOf(false) }
    var pendingUninstallId by remember { mutableStateOf<String?>(null) }
    val pendingUninstall = state.plugins.firstOrNull { it.pluginId == pendingUninstallId }
    val pendingAuthored = state.authoredPlugins.firstOrNull { it.id == pendingUninstallId }
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

    if (showsGitHubInstall) {
        GitHubInstallDialog(
            state = state,
            onInstall = { url -> scope.launchPresenterAction { presenter.inspectGitHub(url) } },
            onConfirm = {
                scope.launchPresenterAction {
                    presenter.confirmInstall()
                    showsGitHubInstall = false
                }
            },
            onUrlChanged = presenter::clearError,
            onDismiss = {
                scope.launchPresenterAction {
                    presenter.cancelInstall()
                    showsGitHubInstall = false
                }
            },
        )
    }

    state.pendingInstall?.takeUnless { showsGitHubInstall }?.let { review ->
        PluginInstallReviewDialog(
            review = review,
            busy = state.busy,
            onConfirm = { scope.launchPresenterAction { presenter.confirmInstall() } },
            onCancel = { scope.launchPresenterAction { presenter.cancelInstall() } },
        )
    }
    if (pendingUninstall != null || pendingAuthored != null) {
        AlertDialog(
            onDismissRequest = { pendingUninstallId = null },
            title = { Text(stringResource(R.string.plugin_uninstall_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        if (pendingAuthored != null) R.string.plugin_ai_delete_message else R.string.plugin_uninstall_confirm_message,
                        pendingAuthored?.let { "${it.name}\n${it.id}" } ?: pendingUninstall?.nameText?.resolveText().orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = {
                        val pluginId = requireNotNull(pendingUninstallId)
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
                        PluginAddMenu(
                            busy = state.busy,
                            agentEnabled = LocalTimelineAppearance.current.aiConfig.agent,
                            onGitHubInstall = {
                                presenter.clearError()
                                showsGitHubInstall = true
                            },
                            onCreateWithAI = onCreateWithAI,
                            onFileInstall = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                        )
                        PluginManagementMenu(
                            state = state,
                            onCheckUpdates = { scope.launchPresenterAction { presenter.checkUpdates() } },
                            onRebuildIndex = { scope.launchPresenterAction { presenter.rebuildIndex() } },
                            onCleanup = { scope.launchPresenterAction { presenter.cleanup() } },
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        ) { padding ->
            PluginManagementList(
                state = state,
                onCancelPluginUpdate = { id -> scope.launchPresenterAction { presenter.cancelPluginUpdate(id) } },
                onUninstall = { pendingUninstallId = it },
                onUpdate = { pluginId -> scope.launchPresenterAction { presenter.inspectGitHubUpdate(pluginId) } },
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
        if (state.busy && !showsGitHubInstall) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
internal fun PluginAddMenu(
    busy: Boolean,
    agentEnabled: Boolean,
    onGitHubInstall: () -> Unit,
    onCreateWithAI: () -> Unit,
    onFileInstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = !busy, onClick = { expanded = true }) {
            FAIcon(FontAwesomeIcons.Solid.Plus, contentDescription = stringResource(R.string.plugin_add))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plugin_install_github)) },
                onClick = {
                    expanded = false
                    onGitHubInstall()
                },
            )
            if (agentEnabled) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plugin_ai_create)) },
                    onClick = {
                        expanded = false
                        onCreateWithAI()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plugin_install)) },
                onClick = {
                    expanded = false
                    onFileInstall()
                },
            )
        }
    }
}

@Composable
internal fun PluginManagementMenu(
    state: PluginManagementStateV1,
    onCheckUpdates: () -> Unit,
    onRebuildIndex: () -> Unit,
    onCleanup: () -> Unit,
) {
    var showsMaintenanceMenu by remember { mutableStateOf(false) }
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plugin_check_updates)) },
                enabled = state.plugins.any { it.sourceUrl != null },
                onClick = {
                    showsMaintenanceMenu = false
                    onCheckUpdates()
                },
            )
            if (state.canRebuildIndex) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plugin_rebuild_index)) },
                    onClick = {
                        showsMaintenanceMenu = false
                        onRebuildIndex()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plugin_cleanup)) },
                enabled = !state.canRebuildIndex,
                onClick = {
                    showsMaintenanceMenu = false
                    onCleanup()
                },
            )
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
                PluginInstallReviewContent(review)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !busy) {
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
private fun PluginInstallReviewContent(review: PluginInstallReviewV1) {
    Text(
        stringResource(
            if (review.sourceUrl ==
                null
            ) {
                R.string.plugin_unverified_warning
            } else {
                R.string.plugin_unverified_github_warning
            },
        ),
        color = MaterialTheme.colorScheme.error,
    )
    review.sourceUrl?.let { Text(it) }
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

@Composable
internal fun PluginPermission.localizedDescription(): String {
    if (type == dev.dimension.flare.data.model.PluginPermissionType.MediaNetwork) {
        return stringResource(R.string.plugin_permission_media_network)
    }
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
internal fun GitHubInstallDialog(
    state: PluginManagementStateV1,
    onInstall: (String) -> Unit,
    onConfirm: () -> Unit,
    onUrlChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val review = state.pendingInstall
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(review?.let { "${it.name} ${it.version}" } ?: stringResource(R.string.plugin_install_github)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (review == null) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            onUrlChanged()
                        },
                        label = { Text(stringResource(R.string.plugin_github_repository)) },
                        placeholder = { Text("https://github.com/owner/repository") },
                        singleLine = true,
                        enabled = !state.busy,
                    )
                } else {
                    PluginInstallReviewContent(review)
                }
                state.error?.let { Text(it.take(512), color = MaterialTheme.colorScheme.error) }
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !state.busy && (review != null || url.isNotBlank()),
                onClick = { if (review == null) onInstall(url) else onConfirm() },
            ) {
                Text(stringResource(R.string.plugin_confirm_install))
            }
        },
        dismissButton = {
            TextButton(enabled = !state.busy, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
internal fun PluginManagementList(
    state: PluginManagementStateV1,
    onCancelPluginUpdate: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onRetryRuntime: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "beta-notice") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FAIcon(FontAwesomeIcons.Solid.CircleInfo, contentDescription = null, modifier = Modifier.size(20.dp))
                    val linkText = stringResource(R.string.plugin_report_issue)
                    val description = stringResource(R.string.plugin_beta_description, linkText)
                    Text(
                        text =
                            buildAnnotatedString {
                                append(description)
                                val start = description.indexOf(linkText)
                                if (start >= 0) {
                                    addLink(
                                        LinkAnnotation.Url(
                                            "https://github.com/DimensionDev/Flare/issues",
                                            TextLinkStyles(
                                                SpanStyle(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textDecoration = TextDecoration.Underline,
                                                ),
                                            ),
                                        ),
                                        start,
                                        start + linkText.length,
                                    )
                                }
                            },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
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
                    title = "${stringResource(R.string.plugin_operation_failed)}: ${state.error.orEmpty().take(512)}",
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
        if (state.items.isEmpty()) {
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
                items = state.items,
                key = { _, plugin -> plugin.pluginId },
            ) { index, plugin ->
                PluginItem(
                    plugin = plugin,
                    busy = state.busy,
                    shapesIndex = index,
                    shapesCount = state.items.size,
                    onCancelUpdate = onCancelPluginUpdate,
                    onUninstall = onUninstall,
                    onUpdate = onUpdate,
                )
            }
        }
    }
}

@Composable
internal fun PluginItem(
    plugin: PluginManagementItemV1,
    busy: Boolean,
    shapesIndex: Int,
    shapesCount: Int,
    onCancelUpdate: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onUpdate: (String) -> Unit,
) {
    val name = plugin.nameText.resolveText().ifBlank { stringResource(R.string.plugin_ai_untitled) }
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes2(shapesIndex, shapesCount),
        leadingContent = {
            AsyncImage(
                model = File(plugin.iconPath),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )
        },
        content = { Text(name) },
        supportingContent = {
            Column {
                Text(
                    plugin.authoring?.let { stringResource(R.string.plugin_ai_source_revision, it.revision) }
                        ?: "${plugin.platformId} · ${plugin.version}",
                )
                plugin.sourceUrl?.let { Text(it) }
                plugin.availableUpdate?.let { update ->
                    TextButton(enabled = !busy, onClick = { onUpdate(plugin.pluginId) }) {
                        Text("${stringResource(R.string.plugin_update)} ${update.tag}")
                    }
                }
                if (plugin.pendingRestart) {
                    Text(
                        stringResource(R.string.plugin_restart_required),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                plugin.authoring?.let { authoring ->
                    if (authoring.phase != PluginAuthoringPhase.Idle && authoring.phase != PluginAuthoringPhase.Applied) {
                        Text(pluginAuthoringLabel(authoring.phase))
                    }
                    authoring.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (authoring.canCancelUpdate) {
                        OutlinedButton(enabled = !busy, onClick = { onCancelUpdate(plugin.pluginId) }) {
                            Text(stringResource(R.string.plugin_ai_stop))
                        }
                    }
                }
            }
        },
        trailingContent = {
            PluginItemMenu(name = name, busy = busy, onUninstall = { onUninstall(plugin.pluginId) })
        },
    )
}

@Composable
internal fun PluginItemMenu(
    name: String,
    busy: Boolean,
    onUninstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = !busy, onClick = { expanded = true }) {
            FAIcon(FontAwesomeIcons.Solid.EllipsisVertical, contentDescription = "${stringResource(R.string.more)}: $name")
        }
        FlareDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plugin_uninstall)) },
                leadingIcon = { FAIcon(FontAwesomeIcons.Solid.Trash, contentDescription = null) },
                colors =
                    MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                enabled = !busy,
                onClick = {
                    expanded = false
                    onUninstall()
                },
            )
        }
    }
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
                PluginInstallWarningTypeV1.SourceChanged -> R.string.plugin_warning_source_changed
                PluginInstallWarningTypeV1.TagMoved -> R.string.plugin_warning_tag_moved
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
