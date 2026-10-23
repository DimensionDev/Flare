package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
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
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingPolicy by remember { mutableStateOf<Pair<MicroBlogKey?, String>?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings = state.settings
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
            if (state.busy || settings == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (settings != null) {
                val globalTitle = stringResource(R.string.proxy_global)
                SegmentedListItem(
                    onClick = { choosingPolicy = null to globalTitle },
                    enabled = !state.busy,
                    shapes = ListItemDefaults.single(),
                    content = { Text(globalTitle) },
                    supportingContent = { Text(settings.global.label(settings)) },
                )
                ProxyFootnote(stringResource(R.string.proxy_description))
                Text(stringResource(R.string.proxy_profiles), style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    settings.profiles.forEachIndexed { index, profile ->
                        var showMenu by remember(profile.id) { mutableStateOf(false) }
                        val inUse = settings.global.proxyId == profile.id || settings.accounts.values.any { it.proxyId == profile.id }
                        SegmentedListItem(
                            onClick = { editingId = profile.id },
                            enabled = !state.busy,
                            shapes = ListItemDefaults.segmentedShapes2(index, settings.profiles.size + 1),
                            content = { Text(profile.name) },
                            supportingContent = {
                                Column {
                                    Text("${profile.protocol.name} · ${profile.host}:${profile.port}")
                                    if (inUse) Text(stringResource(R.string.proxy_in_use))
                                    if (state.isTested(profile)) Text(stringResource(R.string.proxy_test_success))
                                }
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { showMenu = true }, enabled = !state.busy) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.EllipsisVertical,
                                            contentDescription = "${stringResource(R.string.more)}: ${profile.name}",
                                        )
                                    }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.proxy_edit)) }, onClick = {
                                            showMenu = false
                                            editingId = profile.id
                                        })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.proxy_test)) }, onClick = {
                                            showMenu = false
                                            beforeConnect { state.test(profile) }
                                        })
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.proxy_delete)) },
                                            enabled = !inUse,
                                            onClick = {
                                                showMenu = false
                                                state.remove(profile.id)
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                    SegmentedListItem(
                        onClick = { editingId = Uuid.random().toString() },
                        enabled = !state.busy,
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
                            val title = "${account.platformDisplayName} · ${account.accountKey}"
                            val policy = settings.policy(account.accountKey)
                            SegmentedListItem(
                                onClick = { choosingPolicy = account.accountKey to title },
                                enabled = !state.busy,
                                shapes = ListItemDefaults.segmentedShapes2(index, accounts.size),
                                content = { Text(title) },
                                supportingContent = {
                                    Column {
                                        Text(policy.label(settings))
                                        if (policy.mode == ProxyMode.INHERIT) {
                                            Text("${stringResource(R.string.proxy_effective)}: ${settings.global.label(settings)}")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                ProxyFootnote(stringResource(R.string.proxy_scope))
            }
        }
    }
    if (settings != null) {
        choosingPolicy?.let { (accountKey, title) ->
            val options =
                listOf(ProxyPolicy(if (accountKey == null) ProxyMode.SYSTEM else ProxyMode.INHERIT), ProxyPolicy(ProxyMode.DIRECT)) +
                    settings.profiles.map { ProxyPolicy(ProxyMode.PROXY, it.id) }
            AlertDialog(
                onDismissRequest = { choosingPolicy = null },
                title = { Text(title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                        options.forEach { policy ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = settings.policy(accountKey) == policy,
                                        enabled = !state.busy,
                                        role = Role.RadioButton,
                                        onClick = {
                                            choosingPolicy = null
                                            val effective = if (policy.mode == ProxyMode.INHERIT) settings.global else policy
                                            if (effective.mode == ProxyMode.PROXY) {
                                                beforeConnect { state.select(accountKey, policy) }
                                            } else {
                                                state.select(accountKey, policy)
                                            }
                                        },
                                    ).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = settings.policy(accountKey) == policy, onClick = null)
                                Text(policy.label(settings))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { choosingPolicy = null }) { Text(stringResource(R.string.proxy_cancel)) } },
            )
        }
        editingId?.let { id ->
            key(id) {
                val existing = settings.profiles.firstOrNull { it.id == id }
                ProxyEditor(
                    profile = existing ?: ProxyProfile(id, "", ProxyProtocol.HTTPS, "", 443),
                    isNew = existing == null,
                    state = state,
                    beforeConnect = beforeConnect,
                    onClose = { editingId = null },
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
    state: ProxySettingsState,
    beforeConnect: (() -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val name = rememberTextFieldState(profile.name)
    val host = rememberTextFieldState(profile.host)
    val port = rememberTextFieldState(profile.port.toString())
    var protocol by rememberSaveable { mutableStateOf(profile.protocol) }
    var expanded by remember { mutableStateOf(false) }

    fun value() =
        profile.copy(
            name = name.text.toString().trim(),
            protocol = protocol,
            host = host.text.toString().trim(),
            port =
                port.text.toString().toIntOrNull() ?: 0,
        )
    AlertDialog(
        onDismissRequest = { if (!state.busy) onClose() },
        title = { Text(stringResource(if (isNew) R.string.proxy_add else R.string.proxy_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, Modifier.fillMaxWidth(), enabled = !state.busy, label = {
                    Text(stringResource(R.string.proxy_name))
                }, lineLimits = TextFieldLineLimits.SingleLine)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!state.busy) expanded = it }) {
                    OutlinedTextField(
                        value = protocol.name,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !state.busy,
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
                    enabled = !state.busy,
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
                OutlinedTextField(port, Modifier.fillMaxWidth(), enabled = !state.busy, label = {
                    Text(stringResource(R.string.proxy_port))
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), lineLimits = TextFieldLineLimits.SingleLine)
                ProxyFootnote(stringResource(R.string.proxy_no_auth))
                TextButton(
                    onClick = { beforeConnect { state.test(value()) } },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.proxy_test)) }
                ProxyFootnote(stringResource(R.string.proxy_test_hint))
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.isTested(value())) Text(stringResource(R.string.proxy_test_success))
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.save(value(), onClose) }, enabled = !state.busy) { Text(stringResource(R.string.proxy_save)) }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !state.busy) { Text(stringResource(R.string.proxy_cancel)) } },
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
