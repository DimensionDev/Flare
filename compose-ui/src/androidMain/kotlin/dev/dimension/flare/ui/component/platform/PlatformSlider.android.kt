package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val sliderState =
        remember(valueRange) {
            SliderState(value = value, trackRange = valueRange)
        }
    sliderState.value = value

    androidx.compose.material3.Slider(
        state = sliderState,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = {
            onValueChangeFinished?.invoke()
        },
    )
}
