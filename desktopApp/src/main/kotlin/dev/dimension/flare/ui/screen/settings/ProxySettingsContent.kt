package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.Res
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.proxy_accounts
import dev.dimension.flare.proxy_add
import dev.dimension.flare.proxy_cancel
import dev.dimension.flare.proxy_delete
import dev.dimension.flare.proxy_direct
import dev.dimension.flare.proxy_edit
import dev.dimension.flare.proxy_global
import dev.dimension.flare.proxy_host
import dev.dimension.flare.proxy_in_use
import dev.dimension.flare.proxy_inherit
import dev.dimension.flare.proxy_name
import dev.dimension.flare.proxy_no_auth
import dev.dimension.flare.proxy_port
import dev.dimension.flare.proxy_profiles
import dev.dimension.flare.proxy_protocol
import dev.dimension.flare.proxy_save
import dev.dimension.flare.proxy_system
import dev.dimension.flare.proxy_test
import dev.dimension.flare.proxy_test_hint
import dev.dimension.flare.proxy_test_success
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ProxySettingsPresenter
import dev.dimension.flare.ui.presenter.settings.ProxySettingsState
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ComboBox
import io.github.composefluent.component.FluentDialog
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
internal fun ProxySettingsContent() {
    val state = remember { ProxySettingsPresenter() }.invoke()
    ProxySettingsContent(state)
}

@Composable
internal fun ProxySettingsContent(state: ProxySettingsState) {
    var editing by remember { mutableStateOf<ProxyProfile?>(null) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy || state.settings == null) ProgressBar(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = FluentTheme.colors.system.critical) }
        state.settings?.let { settings ->
            ProxyPolicyRow(stringResource(Res.string.proxy_global), settings, null, state)
            Header(stringResource(Res.string.proxy_profiles))
            settings.profiles.forEach { profile ->
                val inUse = settings.global.proxyId == profile.id || settings.accounts.values.any { it.proxyId == profile.id }
                CardExpanderItem(
                    icon = null,
                    heading = { Text(profile.name) },
                    caption = {
                        Column {
                            Text("${profile.protocol.name} · ${profile.host}:${profile.port}")
                            if (inUse) Text(stringResource(Res.string.proxy_in_use))
                            if (state.isTested(profile)) Text(stringResource(Res.string.proxy_test_success))
                        }
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { editing = profile }, disabled = state.busy) { Text(stringResource(Res.string.proxy_edit)) }
                            SubtleButton(onClick = { state.remove(profile.id) }, disabled = state.busy || inUse, iconOnly = true) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Trash,
                                    contentDescription = stringResource(Res.string.proxy_delete),
                                )
                            }
                        }
                    },
                )
            }
            Button(
                onClick = { editing = ProxyProfile(Uuid.random().toString(), "", ProxyProtocol.HTTPS, "", 443) },
                disabled = state.busy,
            ) {
                Text(stringResource(Res.string.proxy_add))
            }
            val accounts = state.accounts.filter { it.accountKey.host != "nostr" }
            if (accounts.isNotEmpty()) {
                Header(stringResource(Res.string.proxy_accounts))
                accounts.forEach { account ->
                    ProxyPolicyRow(account.accountKey.toString(), settings, account.accountKey, state, account)
                }
            }
            editing?.let { profile ->
                key(profile.id) {
                    ProxyEditor(profile, settings.profiles.none { it.id == profile.id }, state, onClose = {
                        state.clearEditorError()
                        editing = null
                    })
                }
            }
        }
    }
}

@Composable
private fun ProxyPolicyRow(
    title: String,
    settings: ProxySettings,
    accountKey: MicroBlogKey?,
    state: ProxySettingsState,
    account: UiAccount? = null,
) {
    val profile = state.accountProfiles[accountKey]
    val policy = settings.policy(accountKey)
    val options =
        listOf(ProxyPolicy(if (accountKey == null) ProxyMode.SYSTEM else ProxyMode.INHERIT), ProxyPolicy(ProxyMode.DIRECT)) +
            settings.profiles.map { ProxyPolicy(ProxyMode.PROXY, it.id) }
    CardExpanderItem(
        icon =
            if (account != null) {
                { AvatarComponent(data = profile?.avatar, size = 24.dp) }
            } else {
                null
            },
        heading = {
            if (profile != null) RichText(profile.name, maxLines = 1) else Text(account?.platformDisplayName ?: title)
        },
        caption = {
            if (account != null) Text(profile?.handle?.canonical ?: account.accountKey.toString(), maxLines = 1)
        },
        trailing = {
            ComboBox(
                modifier = Modifier.widthIn(max = 240.dp).semantics { contentDescription = title },
                items = options.map { it.label(settings) },
                selected = options.indexOf(policy).takeIf { it >= 0 },
                disabled = state.busy,
                onSelectionChange = { index, _ -> state.select(accountKey, options[index]) },
            )
        },
    )
}

@Composable
private fun ProxyPolicy.label(settings: ProxySettings): String =
    when (mode) {
        ProxyMode.SYSTEM -> stringResource(Res.string.proxy_system)
        ProxyMode.DIRECT -> stringResource(Res.string.proxy_direct)
        ProxyMode.INHERIT -> stringResource(Res.string.proxy_inherit)
        ProxyMode.PROXY -> settings.profiles.firstOrNull { it.id == proxyId }?.name ?: proxyId.orEmpty()
    }

@Composable
private fun ProxyEditor(
    profile: ProxyProfile,
    isNew: Boolean,
    state: ProxySettingsState,
    onClose: () -> Unit,
) {
    val name = rememberTextFieldState(profile.name)
    val host = rememberTextFieldState(profile.host)
    val port = rememberTextFieldState(profile.port.toString())
    var protocol by remember { mutableStateOf(profile.protocol) }
    val scrollState = rememberScrollState()

    fun value() =
        profile.copy(
            name = name.text.toString().trim(),
            protocol = protocol,
            host = host.text.toString().trim(),
            port =
                port.text.toString().toIntOrNull() ?: 0,
        )
    FluentDialog(visible = true) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).onKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp && !state.busy) {
                    onClose()
                    true
                } else {
                    false
                }
            },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(if (isNew) Res.string.proxy_add else Res.string.proxy_edit), style = FluentTheme.typography.subtitle)
            FlareScrollBar(scrollState, modifier = Modifier.weight(1f, fill = false)) {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(scrollState).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val nameLabel = stringResource(Res.string.proxy_name)
                    val hostLabel = stringResource(Res.string.proxy_host)
                    val portLabel = stringResource(Res.string.proxy_port)
                    TextField(
                        name,
                        Modifier.fillMaxWidth().semantics {
                            contentDescription = nameLabel
                        },
                        enabled = !state.busy,
                        header = { Text(nameLabel) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    ComboBox(
                        header = stringResource(Res.string.proxy_protocol),
                        items = ProxyProtocol.entries.map { it.name },
                        selected = ProxyProtocol.entries.indexOf(protocol),
                        disabled = state.busy,
                        onSelectionChange = { index, _ -> protocol = ProxyProtocol.entries[index] },
                    )
                    TextField(
                        host,
                        Modifier.fillMaxWidth().semantics {
                            contentDescription = hostLabel
                        },
                        enabled = !state.busy,
                        header = { Text(hostLabel) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    TextField(
                        port,
                        Modifier.fillMaxWidth().semantics {
                            contentDescription = portLabel
                        },
                        enabled = !state.busy,
                        header = {
                            Text(portLabel)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    Text(
                        stringResource(Res.string.proxy_no_auth),
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                    Button(
                        onClick = { state.test(value(), fromEditor = true) },
                        disabled = state.busy,
                    ) { Text(stringResource(Res.string.proxy_test)) }
                    Text(
                        stringResource(Res.string.proxy_test_hint),
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                    if (state.busy) ProgressBar(Modifier.fillMaxWidth())
                    if (state.isTested(value())) Text(stringResource(Res.string.proxy_test_success))
                    state.editorError?.let { Text(it, color = FluentTheme.colors.system.critical) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton(onClick = {
                    state.save(value(), onClose)
                }, disabled = state.busy, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.proxy_save)) }
                Button(
                    onClick = onClose,
                    disabled = state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.proxy_cancel)) }
            }
        }
    }
}
