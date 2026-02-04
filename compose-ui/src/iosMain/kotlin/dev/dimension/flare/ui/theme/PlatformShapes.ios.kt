package dev.dimension.flare.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.slapps.cupertino.theme.CupertinoTheme

internal actual object PlatformShapes {
    actual val extraSmall: Shape
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.shapes.small
    actual val small: Shape
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.shapes.medium
    actual val medium: Shape
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.shapes.large
    actual val large: Shape
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.shapes.extraLarge

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
        get() = CupertinoTheme.shapes.extraLarge

    actual val listCardItemShape: CornerBasedShape
        @Composable
        get() = CupertinoTheme.shapes.extraSmall
    actual val dmShapeFromMe: CornerBasedShape
        @Composable
        @ReadOnlyComposable
        get() =
            CupertinoTheme.shapes.extraLarge.copy(
                bottomEnd = CornerSize(0.dp),
            )
    actual val dmShapeFromOther: CornerBasedShape
        @Composable
        @ReadOnlyComposable
        get() =
            CupertinoTheme.shapes.extraLarge.copy(
                bottomStart = CornerSize(0.dp),
            )
}
