package dev.dimension.flare.ui.theme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape

internal expect object PlatformShapes {
    @get:Composable
    @get:ReadOnlyComposable
    val extraSmall: Shape

    @get:Composable
    @get:ReadOnlyComposable
    val small: Shape

    @get:Composable
    @get:ReadOnlyComposable
    val medium: Shape

    @get:Composable
    @get:ReadOnlyComposable
    val large: Shape

    @get:Composable
    @get:ReadOnlyComposable
    val topCardShape: Shape

    @get:Composable
    @get:ReadOnlyComposable
    val bottomCardShape: Shape

    @get:Composable
    @get:ReadOnlyComposable
    val listCardContainerShape: CornerBasedShape

    @get:Composable
    @get:ReadOnlyComposable
    val listCardItemShape: CornerBasedShape

    @get:Composable
    @get:ReadOnlyComposable
    val dmShapeFromMe: CornerBasedShape

    @get:Composable
    @get:ReadOnlyComposable
    val dmShapeFromOther: CornerBasedShape
}
