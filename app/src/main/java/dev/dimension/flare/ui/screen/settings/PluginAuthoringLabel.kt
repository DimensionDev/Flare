package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.repository.PluginAuthoringPhase

@Composable
internal fun pluginAuthoringLabel(phase: PluginAuthoringPhase): String =
    stringResource(
        when (phase) {
            PluginAuthoringPhase.Idle -> R.string.plugin_ai_idle
            PluginAuthoringPhase.Validating -> R.string.plugin_ai_validating
            PluginAuthoringPhase.WaitingForOperations -> R.string.plugin_ai_waiting
            PluginAuthoringPhase.Applied -> R.string.plugin_ai_applied
            PluginAuthoringPhase.Failed -> R.string.plugin_ai_failed
        },
    )
