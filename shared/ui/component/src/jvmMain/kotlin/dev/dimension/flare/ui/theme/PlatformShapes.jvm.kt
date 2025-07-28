package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import com.konyaco.fluent.FluentTheme

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
}
