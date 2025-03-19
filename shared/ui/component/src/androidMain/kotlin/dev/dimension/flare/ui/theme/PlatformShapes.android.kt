package dev.dimension.flare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

internal actual object PlatformShapes {
    actual val small: Shape
        @Composable
        get() = MaterialTheme.shapes.small
    actual val medium: Shape
        @Composable
        get() = MaterialTheme.shapes.medium
    actual val large: Shape
        @Composable
        get() = MaterialTheme.shapes.large
}
