package dev.dimension.flare.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme

internal actual object PlatformShapes {
    actual val extraSmall: Shape
        @Composable
        get() = FluentTheme.shapes.intersectionEdge
    actual val small: Shape
        @Composable
        get() = FluentTheme.shapes.intersectionEdge
    actual val medium: Shape
        @Composable
        get() = FluentTheme.shapes.control
    actual val large: Shape
        @Composable
        get() = FluentTheme.shapes.overlay
    actual val topCardShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = 8.dp,
                topEnd = 8.dp,
                bottomStart = 4.dp,
                bottomEnd = 4.dp,
            )
    actual val bottomCardShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 4.dp,
                bottomStart = 8.dp,
                bottomEnd = 8.dp,
            )
    actual val listCardContainerShape: CornerBasedShape
        @Composable
        get() = RoundedCornerShape(4.dp)
    actual val listCardItemShape: CornerBasedShape
        @Composable
        get() = RoundedCornerShape(4.dp)
}
