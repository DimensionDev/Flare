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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import dev.dimension.flare.common.DesktopSaveDialog
import dev.dimension.flare.data.model.PluginPermission
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningTypeV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningV1
import dev.dimension.flare.feature.plugin.management.PluginInstallReviewV1
import dev.dimension.flare.feature.plugin.management.PluginManagementItemV1
import dev.dimension.flare.feature.plugin.management.PluginManagementPresenterV1
import dev.dimension.flare.feature.plugin.management.PluginManagementStateV1
import dev.dimension.flare.more
import dev.dimension.flare.plugin_add
import dev.dimension.flare.plugin_ai_create
import dev.dimension.flare.plugin_ai_delete_message
import dev.dimension.flare.plugin_ai_stop
import dev.dimension.flare.plugin_ai_untitled
import dev.dimension.flare.plugin_beta_description
import dev.dimension.flare.plugin_capabilities
import dev.dimension.flare.plugin_check_updates
import dev.dimension.flare.plugin_cleanup
import dev.dimension.flare.plugin_confirm_install
import dev.dimension.flare.plugin_development
import dev.dimension.flare.plugin_empty
import dev.dimension.flare.plugin_github_repository
import dev.dimension.flare.plugin_install
import dev.dimension.flare.plugin_install_github
import dev.dimension.flare.plugin_issue_files_missing
import dev.dimension.flare.plugin_issue_index_corrupt
import dev.dimension.flare.plugin_issue_platform_conflict
import dev.dimension.flare.plugin_issue_platform_invalid
import dev.dimension.flare.plugin_issues
import dev.dimension.flare.plugin_load_versions
import dev.dimension.flare.plugin_operation_failed
import dev.dimension.flare.plugin_permission_account_origin
import dev.dimension.flare.plugin_permission_account_origin_label
import dev.dimension.flare.plugin_permission_cookie
import dev.dimension.flare.plugin_permission_media_network
import dev.dimension.flare.plugin_permissions
import dev.dimension.flare.plugin_rebuild_index
import dev.dimension.flare.plugin_report_issue
import dev.dimension.flare.plugin_restart_required
import dev.dimension.flare.plugin_retry
import dev.dimension.flare.plugin_runtime_fatal
import dev.dimension.flare.plugin_runtime_paused
import dev.dimension.flare.plugin_uninstall
import dev.dimension.flare.plugin_uninstall_confirm_message
import dev.dimension.flare.plugin_uninstall_confirm_title
import dev.dimension.flare.plugin_unverified_github_warning
import dev.dimension.flare.plugin_unverified_warning
import dev.dimension.flare.plugin_update
import dev.dimension.flare.plugin_version
import dev.dimension.flare.plugin_warning_added_permission
import dev.dimension.flare.plugin_warning_changed_package
import dev.dimension.flare.plugin_warning_compatibility
import dev.dimension.flare.plugin_warning_downgrade
import dev.dimension.flare.plugin_warning_source_changed
import dev.dimension.flare.plugin_warning_tag_moved
import dev.dimension.flare.settings_plugins_subtitle
import dev.dimension.flare.settings_plugins_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.resolveText
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.ProvideTextStyle
import io.github.composefluent.background.BackgroundSizing
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Button
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ComboBox
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.InfoBar
import io.github.composefluent.component.InfoBarSeverity
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.io.File

@Composable
internal fun DesktopPluginManagementScreen(
    onBack: () -> Unit,
    onCreateWithAI: () -> Unit = {},
) {
    val window = LocalComposeWindow.current
    val subsystem = koinInject<PluginSubsystemV1>()
    val scope = rememberCoroutineScope()
    val presenter = remember(subsystem, scope) { PluginManagementPresenterV1(subsystem, scope) }
    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }
    val state by presenter.state.collectAsState()
    LaunchedEffect(presenter) { scope.launchPresenterAction { presenter.checkUpdates(forceRefresh = false) } }
    var showsGitHubInstall by remember { mutableStateOf(false) }
    val installLabel = stringResource(Res.string.plugin_install)
    var showsMaintenanceMenu by remember { mutableStateOf(false) }
    var showsDevelopment by remember { mutableStateOf(false) }
    var pendingUninstallId by remember { mutableStateOf<String?>(null) }
    val pendingUninstall = state.plugins.firstOrNull { it.pluginId == pendingUninstallId }
    val pendingAuthored = state.authoredPlugins.firstOrNull { it.id == pendingUninstallId }

    if (showsGitHubInstall) {
        GitHubInstallDialog(
            state = state,
            onLoad = { url -> scope.launchPresenterAction { presenter.loadGitHubRepository(url) } },
            onInstall = { tag ->
                showsGitHubInstall = false
                scope.launchPresenterAction { presenter.inspectGitHub(tag) }
            },
            onUrlChanged = presenter::clearGitHubRepository,
            onDismiss = { showsGitHubInstall = false },
        )
    }

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
        visible = pendingUninstall != null || pendingAuthored != null,
        content = {
            Text(
                stringResource(
                    if (pendingAuthored != null) Res.string.plugin_ai_delete_message else Res.string.plugin_uninstall_confirm_message,
                    pendingAuthored?.let { "${it.name}\n${it.id}" } ?: pendingUninstall?.nameText?.resolveText().orEmpty(),
                ),
            )
        },
        primaryButtonText = stringResource(Res.string.plugin_uninstall),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = { button ->
            if (button == ContentDialogButton.Primary) {
                pendingUninstallId?.let { pluginId ->
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
                PluginAddMenu(
                    busy = state.busy,
                    agentEnabled = LocalTimelineAppearance.current.aiConfig.agent,
                    onGitHubInstall = {
                        presenter.clearGitHubRepository()
                        showsGitHubInstall = true
                    },
                    onCreateWithAI = onCreateWithAI,
                    onFileInstall = {
                        FileDialog(window, installLabel, FileDialog.LOAD).apply {
                            file = "*.fpp"
                            isVisible = true
                            if (directory != null && file != null) {
                                val path = File(directory, file).absolutePath
                                scope.launchPresenterAction { presenter.inspect(path) }
                            }
                        }
                    },
                )
                Box {
                    SubtleButton(
                        onClick = { showsMaintenanceMenu = true },
                        disabled = state.busy,
                        iconOnly = true,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                            contentDescription = stringResource(Res.string.more),
                        )
                    }
                    MenuFlyout(
                        visible = showsMaintenanceMenu,
                        onDismissRequest = { showsMaintenanceMenu = false },
                        placement = FlyoutPlacement.BottomAlignedEnd,
                    ) {
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.plugin_development)) },
                            onClick = {
                                showsMaintenanceMenu = false
                                subsystem.development
                                showsDevelopment = true
                            },
                        )
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.plugin_check_updates)) },
                            enabled = state.plugins.any { it.sourceUrl != null },
                            onClick = {
                                showsMaintenanceMenu = false
                                scope.launchPresenterAction { presenter.checkUpdates() }
                            },
                        )
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
            }
            PluginManagementList(
                state = state,
                onCancelPluginUpdate = { id -> scope.launchPresenterAction { presenter.cancelPluginUpdate(id) } },
                development =
                    if (showsDevelopment || state.development.directory != null) {
                        {
                            PluginDevelopmentPanel(
                                state = state.development,
                                onOpen = {
                                    DesktopSaveDialog.chooseDirectory(window, state.development.recentDirectory)?.let { directory ->
                                        scope.launchPresenterAction { subsystem.development.open(directory.absolutePath) }
                                    }
                                },
                                onReload = { confirmed -> scope.launchPresenterAction { subsystem.development.reload(confirmed) } },
                                onCancel = { subsystem.development.cancelReview() },
                                onStop = { scope.launchPresenterAction { subsystem.development.stop() } },
                            )
                        }
                    } else {
                        null
                    },
                onUninstall = { pendingUninstallId = it },
                onUpdate = { pluginId -> scope.launchPresenterAction { presenter.inspectGitHubUpdate(pluginId) } },
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
internal fun PluginAddMenu(
    busy: Boolean,
    agentEnabled: Boolean,
    onGitHubInstall: () -> Unit,
    onCreateWithAI: () -> Unit,
    onFileInstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SubtleButton(disabled = busy, iconOnly = true, onClick = { expanded = true }) {
            FAIcon(FontAwesomeIcons.Solid.Plus, contentDescription = stringResource(Res.string.plugin_add))
        }
        MenuFlyout(visible = expanded, onDismissRequest = { expanded = false }, placement = FlyoutPlacement.BottomAlignedEnd) {
            MenuFlyoutItem(
                text = { Text(stringResource(Res.string.plugin_install_github)) },
                onClick = {
                    expanded = false
                    onGitHubInstall()
                },
            )
            if (agentEnabled) {
                MenuFlyoutItem(
                    text = { Text(stringResource(Res.string.plugin_ai_create)) },
                    onClick = {
                        expanded = false
                        onCreateWithAI()
                    },
                )
            }
            MenuFlyoutItem(
                text = { Text(stringResource(Res.string.plugin_install)) },
                onClick = {
                    expanded = false
                    onFileInstall()
                },
            )
        }
    }
}

@Composable
internal fun PluginInstallReviewDialog(
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
                Text(
                    stringResource(
                        if (review.sourceUrl ==
                            null
                        ) {
                            Res.string.plugin_unverified_warning
                        } else {
                            Res.string.plugin_unverified_github_warning
                        },
                    ),
                )
                review.sourceUrl?.let { Text(it) }
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
internal fun PluginPermission.localizedDescription(): String {
    if (type == dev.dimension.flare.data.model.PluginPermissionType.MediaNetwork) {
        return stringResource(Res.string.plugin_permission_media_network)
    }
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
private fun GitHubInstallDialog(
    state: PluginManagementStateV1,
    onLoad: (String) -> Unit,
    onInstall: (String) -> Unit,
    onUrlChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val url = rememberTextFieldState()
    var selectedTag by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url.text) {
        selectedTag = null
        onUrlChanged()
    }
    val versions = state.githubCatalog?.versions.orEmpty()
    val selection = versions.firstOrNull { it.tag == selectedTag } ?: versions.firstOrNull()
    ContentDialog(
        visible = true,
        title = stringResource(Res.string.plugin_install_github),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    url,
                    modifier = Modifier.fillMaxWidth(),
                    header = { Text(stringResource(Res.string.plugin_github_repository)) },
                    placeholder = { Text("https://github.com/owner/repository") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    enabled = !state.busy,
                )
                if (selection != null) {
                    ComboBox(
                        header = stringResource(Res.string.plugin_version),
                        items = versions.map { it.tag },
                        selected = versions.indexOf(selection),
                        disabled = state.busy,
                        onSelectionChange = { index, _ -> selectedTag = versions[index].tag },
                    )
                }
                state.error?.let { Text(it.take(512)) }
            }
        },
        primaryButtonText = stringResource(if (selection == null) Res.string.plugin_load_versions else Res.string.plugin_confirm_install),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = { button ->
            if (!state.busy) {
                if (button == ContentDialogButton.Primary) {
                    if (selection == null) onLoad(url.text.toString()) else onInstall(selection.tag)
                } else {
                    onDismiss()
                }
            }
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
    development: (@Composable () -> Unit)? = null,
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
            item(key = "beta-notice") {
                val linkText = stringResource(Res.string.plugin_report_issue)
                val description = stringResource(Res.string.plugin_beta_description, linkText)
                InfoBar(
                    title = {},
                    message = {
                        Text(
                            buildAnnotatedString {
                                append(description)
                                val start = description.indexOf(linkText)
                                if (start >= 0) {
                                    addLink(
                                        LinkAnnotation.Url(
                                            "https://github.com/DimensionDev/Flare/issues",
                                            TextLinkStyles(
                                                SpanStyle(
                                                    color = FluentTheme.colors.text.accent.primary,
                                                    textDecoration = TextDecoration.Underline,
                                                ),
                                            ),
                                        ),
                                        start,
                                        start + linkText.length,
                                    )
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (development != null) {
                item(key = "development") { development() }
            }
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
                    ErrorInfoBar("${stringResource(Res.string.plugin_operation_failed)}: ${state.error.orEmpty().take(512)}")
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
            if (state.items.isEmpty()) {
                item(key = "empty") {
                    CardExpanderItem(
                        heading = { Text(stringResource(Res.string.plugin_empty)) },
                        caption = { Text(stringResource(Res.string.settings_plugins_subtitle)) },
                        icon = null,
                    )
                }
            } else {
                items(
                    state.items,
                    key = PluginManagementItemV1::pluginId,
                ) { plugin ->
                    PluginItem(
                        plugin = plugin,
                        busy =
                            state.busy ||
                                (plugin.authoring == null && state.development.active && state.development.pluginId == plugin.pluginId),
                        onCancelUpdate = onCancelPluginUpdate,
                        onUninstall = onUninstall,
                        onUpdate = onUpdate,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PluginItem(
    plugin: PluginManagementItemV1,
    busy: Boolean,
    onCancelUpdate: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onUpdate: (String) -> Unit,
) {
    val name = plugin.nameText.resolveText().ifBlank { stringResource(Res.string.plugin_ai_untitled) }
    Layer(
        modifier = Modifier.fillMaxWidth(),
        color = FluentTheme.colors.background.card.default,
        backgroundSizing = BackgroundSizing.InnerBorderEdge,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = File(plugin.iconPath),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ProvideTextStyle(FluentTheme.typography.caption.copy(color = FluentTheme.colors.text.text.secondary)) {
                    if (plugin.authoring == null) {
                        Text("${plugin.platformId} · ${plugin.version}")
                    }
                    plugin.sourceUrl?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    plugin.availableUpdate?.let { update ->
                        SubtleButton(disabled = busy, onClick = { onUpdate(plugin.pluginId) }) {
                            Text("${stringResource(Res.string.plugin_update)} ${update.tag}")
                        }
                    }
                    if (plugin.pendingRestart) {
                        Text(stringResource(Res.string.plugin_restart_required))
                    }
                    plugin.authoring?.let { authoring ->
                        if (authoring.phase != PluginAuthoringPhase.Idle && authoring.phase != PluginAuthoringPhase.Applied) {
                            Text(pluginAuthoringLabel(authoring.phase))
                        }
                        authoring.error?.let { Text(it) }
                        if (authoring.canCancelUpdate) {
                            Button(disabled = busy, onClick = { onCancelUpdate(plugin.pluginId) }) {
                                Text(stringResource(Res.string.plugin_ai_stop))
                            }
                        }
                    }
                }
            }
            PluginItemMenu(name = name, busy = busy, onUninstall = { onUninstall(plugin.pluginId) })
        }
    }
}

@Composable
internal fun PluginItemMenu(
    name: String,
    busy: Boolean,
    onUninstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SubtleButton(disabled = busy, iconOnly = true, onClick = { expanded = true }) {
            FAIcon(FontAwesomeIcons.Solid.EllipsisVertical, contentDescription = "${stringResource(Res.string.more)}: $name")
        }
        MenuFlyout(visible = expanded, onDismissRequest = { expanded = false }, placement = FlyoutPlacement.BottomAlignedEnd) {
            MenuFlyoutItem(
                text = { Text(stringResource(Res.string.plugin_uninstall)) },
                icon = { FAIcon(FontAwesomeIcons.Solid.Trash, contentDescription = null) },
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
                PluginInstallWarningTypeV1.SourceChanged -> Res.string.plugin_warning_source_changed
                PluginInstallWarningTypeV1.TagMoved -> Res.string.plugin_warning_tag_moved
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
