package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.feature.agent.presenter.chat.GenericChatPresenter
import dev.dimension.flare.plugin_ai_allow
import dev.dimension.flare.plugin_ai_applied
import dev.dimension.flare.plugin_ai_approval
import dev.dimension.flare.plugin_ai_credentials_changed
import dev.dimension.flare.plugin_ai_enabled
import dev.dimension.flare.plugin_ai_failed
import dev.dimension.flare.plugin_ai_generating
import dev.dimension.flare.plugin_ai_idle
import dev.dimension.flare.plugin_ai_running
import dev.dimension.flare.plugin_ai_stop
import dev.dimension.flare.plugin_ai_untitled
import dev.dimension.flare.plugin_ai_validating
import dev.dimension.flare.plugin_ai_waiting
import dev.dimension.flare.ui.screen.settings.localizedDescription
import io.github.composefluent.component.Button
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun pluginAuthoringLabel(phase: PluginAuthoringPhase): String =
    stringResource(
        when (phase) {
            PluginAuthoringPhase.Idle -> Res.string.plugin_ai_idle
            PluginAuthoringPhase.Generating -> Res.string.plugin_ai_generating
            PluginAuthoringPhase.Validating -> Res.string.plugin_ai_validating
            PluginAuthoringPhase.AwaitingApproval -> Res.string.plugin_ai_approval
            PluginAuthoringPhase.WaitingForOperations -> Res.string.plugin_ai_waiting
            PluginAuthoringPhase.Applied -> Res.string.plugin_ai_applied
            PluginAuthoringPhase.Failed -> Res.string.plugin_ai_failed
        },
    )

@Composable
internal fun PluginAuthoringCard(state: GenericChatPresenter.State) {
    val plugin = state.plugin ?: return
    val enabledLabel = stringResource(Res.string.plugin_ai_enabled)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(plugin.name.ifBlank { stringResource(Res.string.plugin_ai_untitled) })
        Text(pluginAuthoringLabel(plugin.phase))
        if (plugin.running && plugin.phase != PluginAuthoringPhase.Applied) Text(stringResource(Res.string.plugin_ai_running))
        plugin.error?.let { Text(it) }
        plugin.permissions.forEach {
            Text(it.localizedDescription())
        }
        if (plugin.credentialsChanged) Text(stringResource(Res.string.plugin_ai_credentials_changed))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.canApprovePlugin) {
                Button(onClick = state::approvePlugin) { Text(stringResource(Res.string.plugin_ai_allow)) }
            }
            if (state.canStopPlugin) {
                Button(onClick = state::stopPlugin) { Text(stringResource(Res.string.plugin_ai_stop)) }
            }
        }
        if (state.canSetPluginEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switcher(checked = plugin.enabled, onCheckStateChange = state::setPluginEnabled)
                Text(enabledLabel)
            }
        }
    }
}
