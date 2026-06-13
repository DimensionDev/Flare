package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.dimension.flare.common.SystemUtils

@Composable
internal actual fun rippleIndication(
    bounded: Boolean,
    radius: Dp,
    color: Color,
): Indication =
    if (SystemUtils.isMiuiOrHyperOs) {
        LocalIndication.current
    } else {
        ripple(
            bounded = bounded,
            radius = radius,
            color = color,
        )
    }
