package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.composefluent.component.Slider

@Composable
internal actual fun PlatformSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    onValueChangeFinished: (() -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = {
            onValueChangeFinished?.invoke()
        },
        valueRange = valueRange,
    )
}
