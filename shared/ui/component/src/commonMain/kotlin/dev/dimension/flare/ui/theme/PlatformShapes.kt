package dev.dimension.flare.ui.theme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

internal expect object PlatformShapes {
    @get:Composable
    val small: Shape

    @get:Composable
    val medium: Shape

    @get:Composable
    val large: Shape
}
