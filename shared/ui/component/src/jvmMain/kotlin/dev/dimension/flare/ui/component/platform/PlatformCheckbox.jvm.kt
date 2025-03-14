package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.konyaco.fluent.component.CheckBox
import com.konyaco.fluent.component.RadioButton

@Composable
internal actual fun PlatformCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
) {
    CheckBox(
        checked = checked,
        onCheckStateChange = { onCheckedChange?.invoke(it) },
        modifier = modifier,
        enabled = enabled,
//        interactionSource = interactionSource,
    )
}

@Composable
internal actual fun PlatformRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
) {
    RadioButton(
        selected = selected,
        onClick = { onClick?.invoke() },
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    )
}
