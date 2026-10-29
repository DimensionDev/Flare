package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.ui.component.FAIcon

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

@Composable
internal fun PluginAuthoringCard(
    plugin: AuthoredPlugin,
    busy: Boolean,
    onCancelUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                plugin.name.ifBlank { stringResource(R.string.plugin_ai_untitled) },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(enabled = !busy, onClick = onUninstall) {
                FAIcon(
                    FontAwesomeIcons.Solid.Trash,
                    contentDescription = stringResource(R.string.plugin_uninstall),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        SelectionContainer {
            Text(
                plugin.id,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.plugin_ai_revision, plugin.revision),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(pluginAuthoringLabel(plugin.phase), style = MaterialTheme.typography.bodyMedium)
        if (plugin.running && plugin.phase != PluginAuthoringPhase.Applied) {
            Text(
                stringResource(R.string.plugin_ai_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        plugin.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (plugin.canCancelUpdate) {
            OutlinedButton(enabled = !busy, onClick = onCancelUpdate) { Text(stringResource(R.string.plugin_ai_stop)) }
        }
    }
}
