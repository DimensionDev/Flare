package dev.dimension.flare.ui.screen.settings

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AiConfigTextSetting(
    title: String,
    value: String,
    shapes: ListItemShapes,
    onConfirm: (String) -> Unit,
    placeholder: String = "",
    suggestions: List<String> = emptyList(),
    hint: String = "",
    secret: Boolean = false,
    singleLine: Boolean = true,
) {
    var editing by remember { mutableStateOf(false) }
    SegmentedListItem(
        checked = editing,
        onCheckedChange = { editing = it },
        shapes = shapes,
        content = { Text(title) },
        supportingContent = {
            Text(
                if (secret &&
                    value.isNotEmpty()
                ) {
                    "••••••••"
                } else {
                    value.ifBlank { stringResource(R.string.settings_ai_config_value_empty_placeholder) }
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        },
    )
    if (editing) {
        TextEditDialog(
            title = title,
            placeholder = placeholder,
            value = value,
            suggestions = suggestions.toImmutableList(),
            hint = hint,
            secret = secret,
            singleLine = singleLine,
            onDismiss = { editing = false },
            onConfirm = {
                onConfirm(it)
                editing = false
            },
        )
    }
}
