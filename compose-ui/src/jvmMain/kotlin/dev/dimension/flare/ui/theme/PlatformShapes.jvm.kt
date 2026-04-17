package dev.dimension.flare.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal actual object PlatformShapes {
    actual val extraSmall: Shape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(4.dp)
    actual val small: Shape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(8.dp)
    actual val medium: Shape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(12.dp)
    actual val large: Shape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(16.dp)
    actual val topCardShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 4.dp,
            )
    actual val bottomCardShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            )
    actual val listCardContainerShape: CornerBasedShape
        @Composable
        get() = RoundedCornerShape(16.dp)
    actual val listCardItemShape: CornerBasedShape
        @Composable
        get() = RoundedCornerShape(4.dp)
    actual val dmShapeFromMe: CornerBasedShape
        @Composable
        @ReadOnlyComposable
        get() =
            RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 0.dp,
            )
    actual val dmShapeFromOther: CornerBasedShape
        @Composable
        @ReadOnlyComposable
        get() =
            RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 0.dp,
                bottomEnd = 16.dp,
            )
}
