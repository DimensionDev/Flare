package dev.dimension.flare.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.slapps.cupertino.theme.CupertinoTheme

internal actual object PlatformShapes {
    actual val extraSmall: Shape
        @Composable
        get() = CupertinoTheme.shapes.extraSmall
    actual val small: Shape
        @Composable
        get() = CupertinoTheme.shapes.small
    actual val medium: Shape
        @Composable
        get() = CupertinoTheme.shapes.medium
    actual val large: Shape
        @Composable
        get() = CupertinoTheme.shapes.large

    actual val topCardShape: Shape
        @Composable
        get() =
            listCardItemShape.copy(
                topStart = listCardContainerShape.topStart,
                topEnd = listCardContainerShape.topEnd,
            )

    actual val bottomCardShape: Shape
        @Composable
        get() =
            listCardItemShape.copy(
                bottomStart = listCardContainerShape.bottomStart,
                bottomEnd = listCardContainerShape.bottomEnd,
            )

    actual val listCardContainerShape: CornerBasedShape
        @Composable
        get() = CupertinoTheme.shapes.large

    actual val listCardItemShape: CornerBasedShape
        @Composable
        get() = CupertinoTheme.shapes.extraSmall
    actual val dmShapeFromMe: CornerBasedShape
        @Composable
        get() =
            CupertinoTheme.shapes.large.copy(
                bottomEnd = CornerSize(0.dp),
            )
    actual val dmShapeFromOther: CornerBasedShape
        @Composable
        get() =
            CupertinoTheme.shapes.large.copy(
                bottomStart = CornerSize(0.dp),
            )
}