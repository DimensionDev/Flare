package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.feature.agent.presenter.chat.GenericChatPresenter
import dev.dimension.flare.ui.screen.settings.localizedDescription

@Composable
internal fun pluginAuthoringLabel(phase: PluginAuthoringPhase): String =
    stringResource(
        when (phase) {
            PluginAuthoringPhase.Idle -> R.string.plugin_ai_idle
            PluginAuthoringPhase.Generating -> R.string.plugin_ai_generating
            PluginAuthoringPhase.Validating -> R.string.plugin_ai_validating
            PluginAuthoringPhase.AwaitingApproval -> R.string.plugin_ai_approval
            PluginAuthoringPhase.WaitingForOperations -> R.string.plugin_ai_waiting
            PluginAuthoringPhase.Applied -> R.string.plugin_ai_applied
            PluginAuthoringPhase.Failed -> R.string.plugin_ai_failed
        },
    )

@Composable
internal fun PluginAuthoringCard(state: GenericChatPresenter.State) {
    val plugin = state.plugin ?: return
    val enabledLabel = stringResource(R.string.plugin_ai_enabled)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(plugin.name.ifBlank { stringResource(R.string.plugin_ai_untitled) })
        Text(pluginAuthoringLabel(plugin.phase))
        if (plugin.running && plugin.phase != PluginAuthoringPhase.Applied) Text(stringResource(R.string.plugin_ai_running))
        plugin.error?.let { Text(it) }
        plugin.permissions.forEach {
            Text(it.localizedDescription())
        }
        if (plugin.credentialsChanged) Text(stringResource(R.string.plugin_ai_credentials_changed))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plugin.phase == PluginAuthoringPhase.AwaitingApproval) {
                Button(onClick = state::approvePlugin) { Text(stringResource(R.string.plugin_ai_allow)) }
            }
            if (state.room.isRunning ||
                plugin.phase in listOf(PluginAuthoringPhase.AwaitingApproval, PluginAuthoringPhase.WaitingForOperations)
            ) {
                Button(onClick = state::stopPlugin) { Text(stringResource(R.string.plugin_ai_stop)) }
            }
        }
        if (plugin.revision > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = plugin.enabled,
                    onCheckedChange = state::setPluginEnabled,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                enabledLabel
                        },
                )
                Text(enabledLabel)
            }
        }
    }
}
