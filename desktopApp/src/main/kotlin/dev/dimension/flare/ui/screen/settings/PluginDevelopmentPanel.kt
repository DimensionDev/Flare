package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.feature.plugin.development.PluginDevelopmentStateV1
import dev.dimension.flare.plugin_development
import dev.dimension.flare.plugin_development_current
import dev.dimension.flare.plugin_development_description
import dev.dimension.flare.plugin_development_diagnostics
import dev.dimension.flare.plugin_development_open
import dev.dimension.flare.plugin_development_ready
import dev.dimension.flare.plugin_development_reload
import dev.dimension.flare.plugin_development_review
import dev.dimension.flare.plugin_development_schema
import dev.dimension.flare.plugin_development_start
import dev.dimension.flare.plugin_development_stop
import dev.dimension.flare.plugin_development_validating
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PluginDevelopmentPanel(
    state: PluginDevelopmentStateV1,
    onOpen: () -> Unit,
    onReload: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.plugin_development), style = FluentTheme.typography.bodyStrong)
        Text(stringResource(Res.string.plugin_development_description))
        (state.directory ?: state.recentDirectory)?.let { path -> SelectionContainer { Text(path) } }
        state.pluginId?.let { Text("${state.name ?: it} ($it)") }
        if (state.active) Text("#${state.generation}")
        if (state.validating || state.candidateHash != null) {
            Text(
                stringResource(
                    when {
                        state.validating -> Res.string.plugin_development_validating
                        state.active && state.candidateHash == state.runningHash -> Res.string.plugin_development_current
                        else -> Res.string.plugin_development_ready
                    },
                ),
            )
        }
        state.error?.let { SelectionContainer { Text(it, color = FluentTheme.colors.system.critical) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(disabled = state.busy || state.active, onClick = onOpen) {
                Text(stringResource(Res.string.plugin_development_open))
            }
            AccentButton(disabled = state.busy || state.validating || state.candidateHash == null, onClick = { onReload(false) }) {
                Text(stringResource(if (state.active) Res.string.plugin_development_reload else Res.string.plugin_development_start))
            }
            if (state.directory != null) {
                Button(disabled = state.busy, onClick = onStop) { Text(stringResource(Res.string.plugin_development_stop)) }
            }
        }
        var expanded by remember { mutableStateOf(false) }
        if (state.messages.isNotEmpty()) {
            Expander(
                expanded = expanded,
                onExpandedChanged = { expanded = it },
                heading = { Text(stringResource(Res.string.plugin_development_diagnostics)) },
                expandContent = { SelectionContainer { Text(state.messages.joinToString("\n"), modifier = Modifier.padding(12.dp)) } },
            )
        }
    }
    ContentDialog(
        visible = state.reviewHash != null,
        title = stringResource(Res.string.plugin_development),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.plugin_development_review))
                state.permissions.forEach { Text("• ${it.localizedDescription()}") }
                if (state.credentialSchemaChanged) Text(stringResource(Res.string.plugin_development_schema))
            }
        },
        primaryButtonText = stringResource(if (state.active) Res.string.plugin_development_reload else Res.string.plugin_development_start),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = { button ->
            if (!state.busy) {
                if (button == ContentDialogButton.Primary) onReload(true) else onCancel()
            }
        },
    )
}
