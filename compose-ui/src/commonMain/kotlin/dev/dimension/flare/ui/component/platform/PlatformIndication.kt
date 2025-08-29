package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
internal expect fun rippleIndication(
    bounded: Boolean,
    radius: Dp,
    color: Color,
): Indication
