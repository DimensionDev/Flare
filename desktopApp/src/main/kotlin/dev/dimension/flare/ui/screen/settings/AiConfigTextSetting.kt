package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.dimension.flare.Res
import dev.dimension.flare.edit
import dev.dimension.flare.settings_ai_config_value_empty_placeholder
import io.github.composefluent.component.Button
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AiConfigTextSetting(
    title: String,
    value: String,
    onConfirm: (String) -> Unit,
    placeholder: String = "",
    suggestions: List<String> = emptyList(),
    hint: String = "",
    secret: Boolean = false,
    singleLine: Boolean = true,
) {
    var editing by remember { mutableStateOf<TextEditDialogState?>(null) }
    ExpanderItem(
        heading = { Text(title) },
        caption = {
            Text(
                if (secret &&
                    value.isNotEmpty()
                ) {
                    "••••••••"
                } else {
                    value.ifBlank { stringResource(Res.string.settings_ai_config_value_empty_placeholder) }
                },
                maxLines = 2,
            )
        },
        trailing = {
            Button(
                modifier = Modifier.semantics { contentDescription = title },
                onClick = {
                    editing =
                        TextEditDialogState(
                            title = title,
                            placeholder = placeholder,
                            value = value,
                            suggestions = suggestions.toImmutableList(),
                            hint = hint,
                            secret = secret,
                            singleLine = singleLine,
                            onConfirm = onConfirm,
                        )
                },
            ) { Text(stringResource(Res.string.edit)) }
        },
    )
    editing?.let { dialog -> SettingsTextEditDialog(dialog, onDismiss = { editing = null }) }
}
