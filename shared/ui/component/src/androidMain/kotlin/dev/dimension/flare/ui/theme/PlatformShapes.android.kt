package dev.dimension.flare.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    actual val topCardShape: Shape
        @Composable
        get() =
            listCardItemShape.copy(
                topStart = listCardContainerShape.topStart,
                topEnd = listCardContainerShape.topEnd,
            )

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    actual val bottomCardShape: Shape
        @Composable
        get() =
            listCardItemShape.copy(
                bottomStart = listCardContainerShape.bottomStart,
                bottomEnd = listCardContainerShape.bottomEnd,
            )

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    actual val listCardContainerShape: CornerBasedShape
        @Composable
        get() = MaterialTheme.shapes.largeIncreased

    actual val listCardItemShape: CornerBasedShape
        @Composable
        get() = MaterialTheme.shapes.extraSmall
}

public object ListCardShapes {
    @Composable
    public fun container(): Shape = PlatformShapes.listCardContainerShape

    @Composable
    public fun item(): Shape = PlatformShapes.listCardItemShape

    @Composable
    public fun topCard(): Shape = PlatformShapes.topCardShape

    @Composable
    public fun bottomCard(): Shape = PlatformShapes.bottomCardShape
}
