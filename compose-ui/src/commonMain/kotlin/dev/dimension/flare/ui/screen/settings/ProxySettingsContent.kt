package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.proxy_accounts
import dev.dimension.flare.compose.ui.proxy_add
import dev.dimension.flare.compose.ui.proxy_cancel
import dev.dimension.flare.compose.ui.proxy_delete
import dev.dimension.flare.compose.ui.proxy_description
import dev.dimension.flare.compose.ui.proxy_direct
import dev.dimension.flare.compose.ui.proxy_edit
import dev.dimension.flare.compose.ui.proxy_effective
import dev.dimension.flare.compose.ui.proxy_global
import dev.dimension.flare.compose.ui.proxy_host
import dev.dimension.flare.compose.ui.proxy_in_use
import dev.dimension.flare.compose.ui.proxy_inherit
import dev.dimension.flare.compose.ui.proxy_name
import dev.dimension.flare.compose.ui.proxy_no_auth
import dev.dimension.flare.compose.ui.proxy_port
import dev.dimension.flare.compose.ui.proxy_profiles
import dev.dimension.flare.compose.ui.proxy_save
import dev.dimension.flare.compose.ui.proxy_scope
import dev.dimension.flare.compose.ui.proxy_system
import dev.dimension.flare.compose.ui.proxy_test
import dev.dimension.flare.compose.ui.proxy_test_hint
import dev.dimension.flare.compose.ui.proxy_test_success
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.platform.PlatformButton
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenu
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuItem
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextButton
import dev.dimension.flare.ui.component.platform.PlatformTextField
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ProxySettingsPresenter
import dev.dimension.flare.ui.presenter.settings.ProxySettingsState
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
public fun ProxySettingsContent(
    modifier: Modifier = Modifier,
    beforeConnect: (() -> Unit) -> Unit = { it() },
) {
    val state = remember { ProxySettingsPresenter() }.invoke()
    var editing by remember { mutableStateOf<ProxyProfile?>(null) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlatformText(stringResource(Res.string.proxy_description))
        PlatformText(stringResource(Res.string.proxy_scope))
        state.error?.let { PlatformText(it) }
        val settings = state.settings ?: return@Column
        val profile = editing
        if (profile != null) {
            key(profile.id) {
                ProxyEditor(profile, state, beforeConnect, onClose = { editing = null })
            }
        } else {
            PlatformText(stringResource(Res.string.proxy_global))
            ProxyPolicyPicker(settings, null, state, beforeConnect)
            PlatformText(stringResource(Res.string.proxy_profiles))
            settings.profiles.forEach { item ->
                val inUse = settings.global.proxyId == item.id || settings.accounts.values.any { it.proxyId == item.id }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlatformText(item.name)
                    PlatformText("${item.protocol.name} · ${item.host}:${item.port}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlatformTextButton(
                            onClick = { editing = item },
                            enabled = !state.busy,
                        ) { PlatformText(stringResource(Res.string.proxy_edit)) }
                        PlatformTextButton(onClick = {
                            beforeConnect { state.test(item) }
                        }, enabled = !state.busy) { PlatformText(stringResource(Res.string.proxy_test)) }
                        PlatformTextButton(onClick = {
                            state.remove(item.id)
                        }, enabled = !state.busy && !inUse) { PlatformText(stringResource(Res.string.proxy_delete)) }
                    }
                    if (inUse) PlatformText(stringResource(Res.string.proxy_in_use))
                    if (state.isTested(item)) PlatformText(stringResource(Res.string.proxy_test_success))
                }
            }
            PlatformButton(
                onClick = { editing = ProxyProfile(Uuid.random().toString(), "", ProxyProtocol.HTTPS, "", 443) },
                enabled = !state.busy,
            ) {
                PlatformText(stringResource(Res.string.proxy_add))
            }
            PlatformText(stringResource(Res.string.proxy_accounts))
            state.accounts.filter { it.accountKey.host != "nostr" }.forEach { account ->
                PlatformText("${account.platformDisplayName} · ${account.accountKey}")
                ProxyPolicyPicker(settings, account.accountKey, state, beforeConnect)
            }
        }
    }
}

@Composable
private fun ProxyPolicyPicker(
    settings: ProxySettings,
    accountKey: MicroBlogKey?,
    state: ProxySettingsState,
    beforeConnect: (() -> Unit) -> Unit,
) {
    val policy = settings.policy(accountKey)
    val options =
        listOf(ProxyPolicy(if (accountKey == null) ProxyMode.SYSTEM else ProxyMode.INHERIT), ProxyPolicy(ProxyMode.DIRECT)) +
            settings.profiles.map { ProxyPolicy(ProxyMode.PROXY, it.id) }
    var expanded by remember { mutableStateOf(false) }
    Box {
        PlatformButton(onClick = { expanded = true }, enabled = !state.busy) { PlatformText(policy.label(settings)) }
        PlatformDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                PlatformDropdownMenuItem(text = { PlatformText(option.label(settings)) }, onClick = {
                    expanded = false
                    val effective = if (option.mode == ProxyMode.INHERIT) settings.global else option
                    if (effective.mode == ProxyMode.PROXY) {
                        beforeConnect { state.select(accountKey, option) }
                    } else {
                        state.select(accountKey, option)
                    }
                })
            }
        }
    }
    if (policy.mode == ProxyMode.INHERIT) PlatformText("${stringResource(Res.string.proxy_effective)}: ${settings.global.label(settings)}")
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
    state: ProxySettingsState,
    beforeConnect: (() -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val name = rememberTextFieldState(profile.name)
    val host = rememberTextFieldState(profile.host)
    val port = rememberTextFieldState(profile.port.toString())
    var protocol by remember { mutableStateOf(profile.protocol) }
    var expanded by remember { mutableStateOf(false) }
    PlatformTextField(name, Modifier.fillMaxWidth(), label = {
        PlatformText(stringResource(Res.string.proxy_name))
    }, lineLimits = TextFieldLineLimits.SingleLine)
    Box {
        PlatformButton(onClick = { expanded = true }, enabled = !state.busy) { PlatformText(protocol.name) }
        PlatformDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            ProxyProtocol.entries.forEach { value ->
                PlatformDropdownMenuItem(text = { PlatformText(value.name) }, onClick = {
                    protocol = value
                    expanded = false
                })
            }
        }
    }
    PlatformText(stringResource(Res.string.proxy_no_auth))
    PlatformTextField(host, Modifier.fillMaxWidth(), label = {
        PlatformText(stringResource(Res.string.proxy_host))
    }, lineLimits = TextFieldLineLimits.SingleLine)
    PlatformTextField(port, Modifier.fillMaxWidth(), label = {
        PlatformText(stringResource(Res.string.proxy_port))
    }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), lineLimits = TextFieldLineLimits.SingleLine)

    fun value() =
        profile.copy(
            name = name.text.toString().trim(),
            protocol = protocol,
            host = host.text.toString().trim(),
            port =
                port.text.toString().toIntOrNull() ?: 0,
        )
    PlatformText(stringResource(Res.string.proxy_test_hint))
    if (state.isTested(value())) PlatformText(stringResource(Res.string.proxy_test_success))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlatformButton(onClick = {
            state.save(value(), onClose)
        }, enabled = !state.busy) { PlatformText(stringResource(Res.string.proxy_save)) }
        PlatformTextButton(onClick = {
            beforeConnect { state.test(value()) }
        }, enabled = !state.busy) { PlatformText(stringResource(Res.string.proxy_test)) }
        PlatformTextButton(onClick = onClose, enabled = !state.busy) { PlatformText(stringResource(Res.string.proxy_cancel)) }
    }
}
