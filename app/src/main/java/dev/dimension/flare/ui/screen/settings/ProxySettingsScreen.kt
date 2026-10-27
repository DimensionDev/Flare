package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.ProxySettingsError
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.isLoading
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ProxyEditorPresenter
import dev.dimension.flare.ui.presenter.settings.ProxyEditorState
import dev.dimension.flare.ui.presenter.settings.ProxySettingsPresenter
import dev.dimension.flare.ui.presenter.settings.ProxySettingsState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import dev.dimension.flare.ui.theme.single
import kotlin.uuid.Uuid

@Composable
internal fun ProxySettingsScreen(onBack: () -> Unit) {
    val state = remember { ProxySettingsPresenter() }.invoke()
    ProxySettingsContent(state, onBack, rememberProxyPermission())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProxySettingsContent(
    state: ProxySettingsState,
    onBack: () -> Unit,
    beforeConnect: (() -> Unit) -> Unit,
    editorState: @Composable () -> ProxyEditorState = { remember { ProxyEditorPresenter() }.invoke() },
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingGlobal by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings = state.settings.takeSuccess()
    FlareScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.proxy_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settings != null) {
                val globalTitle = stringResource(R.string.proxy_global)
                SegmentedListItem(
                    checked = choosingGlobal,
                    onCheckedChange = { choosingGlobal = it },
                    enabled = state.operation?.isLoading != true,
                    shapes = ListItemDefaults.single(),
                    content = { Text(globalTitle) },
                    trailingContent = {
                        ProxyPolicyDropdown(state, null, globalTitle, choosingGlobal, { choosingGlobal = it }, beforeConnect)
                    },
                )
                Text(stringResource(R.string.proxy_profiles), style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    settings.profiles.forEachIndexed { index, profile ->
                        val inUse = settings.global.proxyId == profile.id || settings.accounts.values.any { it.proxyId == profile.id }
                        SegmentedListItem(
                            onClick = { editingId = profile.id },
                            enabled = state.operation?.isLoading != true,
                            shapes = ListItemDefaults.segmentedShapes2(index, settings.profiles.size + 1),
                            content = { Text(profile.name) },
                            supportingContent = {
                                Text("${profile.protocol.name} · ${profile.host}:${profile.port}")
                            },
                            trailingContent = {
                                IconButton(onClick = { state.remove(profile.id) }, enabled = state.operation?.isLoading != true && !inUse) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Trash,
                                        contentDescription = "${stringResource(R.string.proxy_delete)}: ${profile.name}",
                                    )
                                }
                            },
                        )
                    }
                    SegmentedListItem(
                        onClick = { editingId = Uuid.random().toString() },
                        enabled = state.operation?.isLoading != true,
                        shapes = ListItemDefaults.segmentedShapes2(settings.profiles.size, settings.profiles.size + 1),
                        content = { Text(stringResource(R.string.proxy_add)) },
                        leadingContent = { FAIcon(FontAwesomeIcons.Solid.Plus, contentDescription = null) },
                    )
                }
                val accounts = state.accounts.filter { it.accountKey.host != "nostr" }
                if (accounts.isNotEmpty()) {
                    Text(stringResource(R.string.proxy_accounts), style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        accounts.forEachIndexed { index, account ->
                            val profile = state.accountProfiles[account.accountKey]
                            var expanded by remember(account.accountKey) { mutableStateOf(false) }
                            val label = "${stringResource(R.string.proxy_title)}: ${state.accountLabel(account)}"
                            SegmentedListItem(
                                checked = expanded,
                                onCheckedChange = { expanded = it },
                                enabled = state.operation?.isLoading != true,
                                shapes = ListItemDefaults.segmentedShapes2(index, accounts.size),
                                leadingContent = { AvatarComponent(data = profile?.avatar) },
                                content = {
                                    if (profile != null) RichText(profile.name, maxLines = 1) else Text(account.platformDisplayName)
                                },
                                supportingContent = {
                                    Text(
                                        profile?.handle?.canonical ?: account.accountKey.toString(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    ProxyPolicyDropdown(state, account.accountKey, label, expanded, { expanded = it }, beforeConnect)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (settings != null) {
        editingId?.let { id ->
            key(id) {
                val existing = settings.profiles.firstOrNull { it.id == id }
                ProxyEditor(
                    profile = existing ?: ProxyProfile(id, "", ProxyProtocol.HTTPS, "", 443),
                    isNew = existing == null,
                    stateProvider = editorState,
                    beforeConnect = beforeConnect,
                    onClose = {
                        editingId = null
                    },
                )
            }
        }
    }
    state.error?.let { error ->
        var dismissed by remember(error) { mutableStateOf(false) }
        if (!dismissed) {
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text(stringResource(R.string.proxy_title)) },
                text = { Text(error.localizedMessage()) },
                confirmButton = {
                    TextButton(onClick = {
                        dismissed = true
                        if (settings == null) state.retry()
                    }) {
                        Text(stringResource(if (settings == null) R.string.status_loadmore_error_retry else android.R.string.ok))
                    }
                },
            )
        }
    }
}

@Composable
private fun ProxyPolicyDropdown(
    state: ProxySettingsState,
    accountKey: MicroBlogKey?,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    beforeConnect: (() -> Unit) -> Unit,
) {
    val settings = state.settings.takeSuccess() ?: return
    val policy = settings.policy(accountKey)
    Box {
        TextButton(
            onClick = { onExpandedChange(true) },
            enabled = state.operation?.isLoading != true,
            modifier = Modifier.widthIn(max = 160.dp).semantics { contentDescription = label },
        ) {
            Text(policy.label(settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            val options =
                listOf(ProxyPolicy(if (accountKey == null) ProxyMode.SYSTEM else ProxyMode.INHERIT), ProxyPolicy(ProxyMode.DIRECT)) +
                    settings.profiles.map { ProxyPolicy(ProxyMode.PROXY, it.id) }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label(settings)) },
                    enabled = state.operation?.isLoading != true,
                    modifier = Modifier.semantics { selected = policy == option },
                    onClick = {
                        onExpandedChange(false)
                        val effective = if (option.mode == ProxyMode.INHERIT) settings.global else option
                        if (effective.mode == ProxyMode.PROXY) {
                            beforeConnect { state.select(accountKey, option) }
                        } else {
                            state.select(accountKey, option)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProxyPolicy.label(settings: ProxySettings): String =
    when (mode) {
        ProxyMode.SYSTEM -> stringResource(R.string.proxy_system)
        ProxyMode.DIRECT -> stringResource(R.string.proxy_direct)
        ProxyMode.INHERIT -> stringResource(R.string.proxy_inherit)
        ProxyMode.PROXY -> settings.profiles.firstOrNull { it.id == proxyId }?.name ?: proxyId.orEmpty()
    }

@Composable
private fun ProxyFootnote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyEditor(
    profile: ProxyProfile,
    isNew: Boolean,
    stateProvider: @Composable () -> ProxyEditorState,
    beforeConnect: (() -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val state = stateProvider()
    val isLoading = state.saveState is UiState.Loading || state.testState is UiState.Loading
    val name = rememberTextFieldState(profile.name)
    val host = rememberTextFieldState(profile.host)
    val port = rememberTextFieldState(profile.port.toString())
    var protocol by rememberSaveable { mutableStateOf(profile.protocol) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveState) {
        if (state.saveState is UiState.Success) onClose()
    }

    fun value() =
        profile.copy(
            name = name.text.toString().trim(),
            protocol = protocol,
            host = host.text.toString().trim(),
            port =
                port.text.toString().toIntOrNull() ?: 0,
        )
    AlertDialog(
        onDismissRequest = { if (!isLoading) onClose() },
        title = { Text(stringResource(if (isNew) R.string.proxy_add else R.string.proxy_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, Modifier.fillMaxWidth(), enabled = !isLoading, label = {
                    Text(stringResource(R.string.proxy_name))
                }, lineLimits = TextFieldLineLimits.SingleLine)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!isLoading) expanded = it }) {
                    OutlinedTextField(
                        value = protocol.name,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        label = { Text(stringResource(R.string.proxy_protocol)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ProxyProtocol.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = {
                                protocol = option
                                expanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    host,
                    Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    label = {
                        Text(stringResource(R.string.proxy_host))
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                        ),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                OutlinedTextField(port, Modifier.fillMaxWidth(), enabled = !isLoading, label = {
                    Text(stringResource(R.string.proxy_port))
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), lineLimits = TextFieldLineLimits.SingleLine)
                ProxyFootnote(stringResource(R.string.proxy_no_auth))
                TextButton(
                    onClick = { beforeConnect { state.test(value()) } },
                    enabled = !isLoading,
                ) { Text(stringResource(R.string.proxy_test)) }
                ProxyFootnote(stringResource(R.string.proxy_test_hint))
                if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.isTested(value())) Text(stringResource(R.string.proxy_test_success))
                state.error?.let { Text(it.localizedMessage(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.save(value()) }, enabled = !isLoading) { Text(stringResource(R.string.proxy_save)) }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !isLoading) { Text(stringResource(R.string.proxy_cancel)) } },
    )
}

@Composable
private fun rememberProxyPermission(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) { granted ->
            if (granted) {
                pending?.invoke()
            } else {
                android.widget.Toast
                    .makeText(context, R.string.proxy_local_network_permission, android.widget.Toast.LENGTH_LONG)
                    .show()
            }
            pending = null
        }
    return { action ->
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (android.os.Build.VERSION.SDK_INT < 37 ||
            androidx.core.content.ContextCompat
                .checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pending = action
            launcher.launch(permission)
        }
    }
}

@Composable
private fun ProxySettingsError.localizedMessage(): String =
    stringResource(
        when (this) {
            ProxySettingsError.NAME_REQUIRED -> R.string.proxy_error_name
            ProxySettingsError.INVALID_HOST -> R.string.proxy_error_host
            ProxySettingsError.INVALID_PORT -> R.string.proxy_error_port
            ProxySettingsError.IN_USE -> R.string.proxy_error_in_use
            ProxySettingsError.INVALID_POLICY -> R.string.proxy_error_policy
            ProxySettingsError.REQUEST_FAILED -> R.string.proxy_error_request
        },
    )
