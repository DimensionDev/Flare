package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.Indication
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
internal actual fun rippleIndication(
    bounded: Boolean,
    radius: Dp,
    color: Color,
): Indication =
    ripple(
        bounded = bounded,
        radius = radius,
        color = color,
    )
