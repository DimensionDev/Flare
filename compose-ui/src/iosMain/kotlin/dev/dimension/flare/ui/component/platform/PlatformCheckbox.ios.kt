package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.slapps.cupertino.CupertinoCheckBox

@Composable
internal actual fun PlatformCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
) {
    CupertinoCheckBox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
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
    CupertinoCheckBox(
        checked = selected,
        onCheckedChange = { onClick?.invoke() },
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    )
}
