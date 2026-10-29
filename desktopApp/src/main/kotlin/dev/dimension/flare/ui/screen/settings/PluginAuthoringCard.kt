package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.Res
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.plugin_ai_applied
import dev.dimension.flare.plugin_ai_failed
import dev.dimension.flare.plugin_ai_idle
import dev.dimension.flare.plugin_ai_revision
import dev.dimension.flare.plugin_ai_running
import dev.dimension.flare.plugin_ai_stop
import dev.dimension.flare.plugin_ai_untitled
import dev.dimension.flare.plugin_ai_validating
import dev.dimension.flare.plugin_ai_waiting
import dev.dimension.flare.plugin_uninstall
import dev.dimension.flare.ui.component.FAIcon
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
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

@Composable
internal fun PluginAuthoringCard(
    plugin: AuthoredPlugin,
    busy: Boolean,
    onCancelUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                plugin.name.ifBlank {
                    stringResource(Res.string.plugin_ai_untitled)
                },
                modifier = Modifier.weight(1f),
                style = FluentTheme.typography.bodyStrong,
            )
            SubtleButton(disabled = busy, iconOnly = true, onClick = onUninstall) {
                FAIcon(FontAwesomeIcons.Solid.Trash, contentDescription = stringResource(Res.string.plugin_uninstall))
            }
        }
        SelectionContainer {
            Text(
                plugin.id,
                style = FluentTheme.typography.caption,
                fontFamily = FontFamily.Monospace,
                color = FluentTheme.colors.text.text.secondary,
            )
        }
        Text(
            stringResource(Res.string.plugin_ai_revision, plugin.revision),
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary,
        )
        if (plugin.phase != PluginAuthoringPhase.Idle) {
            Text(pluginAuthoringLabel(plugin.phase))
        }
        if (plugin.running && plugin.phase != PluginAuthoringPhase.Applied) {
            Text(
                stringResource(Res.string.plugin_ai_running),
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary,
            )
        }
        plugin.error?.let { Text(it, style = FluentTheme.typography.caption) }
        if (plugin.canCancelUpdate) {
            Button(disabled = busy, onClick = onCancelUpdate) { Text(stringResource(Res.string.plugin_ai_stop)) }
        }
    }
}
