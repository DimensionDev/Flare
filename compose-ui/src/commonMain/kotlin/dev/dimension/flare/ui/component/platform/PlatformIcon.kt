package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import dev.dimension.flare.ui.theme.PlatformContentColor

@Composable
internal expect fun PlatformIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = PlatformContentColor.current,
)

@Composable
internal expect fun PlatformIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = PlatformContentColor.current,
)
