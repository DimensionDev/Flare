package dev.dimension.flare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

internal actual object PlatformShapes {
    actual val extraSmall: Shape
        @Composable
        get() = MaterialTheme.shapes.extraSmall
    actual val small: Shape
        @Composable
        get() = MaterialTheme.shapes.small
    actual val medium: Shape
        @Composable
        get() = MaterialTheme.shapes.medium
    actual val large: Shape
        @Composable
        get() = MaterialTheme.shapes.large
    actual val topCardShape: Shape
        @Composable
        get() =
            MaterialTheme.shapes.extraSmall.copy(
                topStart = MaterialTheme.shapes.medium.topStart,
                topEnd = MaterialTheme.shapes.medium.topEnd,
            )
    actual val bottomCardShape: Shape
        @Composable
        get() =
            MaterialTheme.shapes.extraSmall.copy(
                bottomStart = MaterialTheme.shapes.medium.bottomStart,
                bottomEnd = MaterialTheme.shapes.medium.bottomEnd,
            )
}
