package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.composefluent.component.Icon

@Composable
internal actual fun PlatformIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
internal actual fun PlatformIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
