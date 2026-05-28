package dev.dimension.flare.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = FlareDividerDefaults.thickness,
    color: Color = FlareDividerDefaults.color,
) = Canvas(modifier.fillMaxWidth().height(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(0f, thickness.toPx() / 2),
        end = Offset(size.width, thickness.toPx() / 2),
    )
}

@Composable
internal fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = FlareDividerDefaults.thickness,
    color: Color = FlareDividerDefaults.color,
) = Canvas(modifier.fillMaxHeight().width(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(thickness.toPx() / 2, 0f),
        end = Offset(thickness.toPx() / 2, size.height),
    )
}

public object FlareDividerDefaults {
    public val color: Color
        @Composable
        get() = PlatformTheme.colorScheme.outline

    public val thickness: Dp = 0.8.dp
}
