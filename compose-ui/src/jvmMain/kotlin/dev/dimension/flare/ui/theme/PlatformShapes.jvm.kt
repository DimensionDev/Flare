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
                topStart = FluentTheme.cornerRadius.overlay,
                topEnd = FluentTheme.cornerRadius.overlay,
                bottomStart = FluentTheme.cornerRadius.control,
                bottomEnd = FluentTheme.cornerRadius.control,
            )
    actual val bottomCardShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = FluentTheme.cornerRadius.control,
                topEnd = FluentTheme.cornerRadius.control,
                bottomStart = FluentTheme.cornerRadius.overlay,
                bottomEnd = FluentTheme.cornerRadius.overlay,
            )
    actual val listCardContainerShape: CornerBasedShape
        @Composable
        get() = RoundedCornerShape(FluentTheme.cornerRadius.overlay)
    actual val listCardItemShape: CornerBasedShape
        @Composable
        get() = RoundedCornerShape(FluentTheme.cornerRadius.control)
    actual val dmShapeFromMe: CornerBasedShape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = FluentTheme.cornerRadius.overlay,
                topEnd = FluentTheme.cornerRadius.overlay,
                bottomStart = FluentTheme.cornerRadius.overlay,
                bottomEnd = 0.dp,
            )
    actual val dmShapeFromOther: CornerBasedShape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = FluentTheme.cornerRadius.overlay,
                topEnd = FluentTheme.cornerRadius.overlay,
                bottomStart = 0.dp,
                bottomEnd = FluentTheme.cornerRadius.overlay,
            )
}
