package dev.dimension.flare.ui.theme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

internal expect object PlatformShapes {
    @get:Composable
    val extraSmall: Shape

    @get:Composable
    val small: Shape

    @get:Composable
    val medium: Shape

    @get:Composable
    val large: Shape

    @get:Composable
    val topCardShape: Shape

    @get:Composable
    val bottomCardShape: Shape

    @get:Composable
    val listCardContainerShape: CornerBasedShape

    @get:Composable
    val listCardItemShape: CornerBasedShape
}
