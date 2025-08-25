package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    onValueChangeFinished: (() -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
    )
}
