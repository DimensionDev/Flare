package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import dev.dimension.flare.Res
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.plugin_ai_applied
import dev.dimension.flare.plugin_ai_failed
import dev.dimension.flare.plugin_ai_idle
import dev.dimension.flare.plugin_ai_validating
import dev.dimension.flare.plugin_ai_waiting
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun pluginAuthoringLabel(phase: PluginAuthoringPhase): String =
    stringResource(
        when (phase) {
            PluginAuthoringPhase.Idle -> Res.string.plugin_ai_idle
            PluginAuthoringPhase.Validating -> Res.string.plugin_ai_validating
            PluginAuthoringPhase.WaitingForOperations -> Res.string.plugin_ai_waiting
            PluginAuthoringPhase.Applied -> Res.string.plugin_ai_applied
            PluginAuthoringPhase.Failed -> Res.string.plugin_ai_failed
        },
    )
