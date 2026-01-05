package dev.dimension.flare.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        get() = ListItemDefaults.first().shape

    actual val bottomCardShape: Shape
        @Composable
        get() = ListItemDefaults.last().shape

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    actual val listCardContainerShape: CornerBasedShape
        @Composable
        get() = ListItemDefaults.single().shape as? CornerBasedShape ?: MaterialTheme.shapes.large

    actual val listCardItemShape: CornerBasedShape
        @Composable
        get() = ListItemDefaults.item().shape as? CornerBasedShape ?: MaterialTheme.shapes.extraSmall
    actual val dmShapeFromMe: CornerBasedShape
        @Composable
        get() =
            MaterialTheme.shapes.largeIncreased.copy(
                bottomEnd = CornerSize(0.dp),
            )
    actual val dmShapeFromOther: CornerBasedShape
        @Composable
        get() =
            MaterialTheme.shapes.largeIncreased.copy(
                bottomStart = CornerSize(0.dp),
            )
}

public object ListCardShapes {
    @Composable
    public fun container(): CornerBasedShape = PlatformShapes.listCardContainerShape

    @Composable
    public fun item(): Shape = PlatformShapes.listCardItemShape

    @Composable
    public fun topCard(): Shape = PlatformShapes.topCardShape

    @Composable
    public fun bottomCard(): Shape = PlatformShapes.bottomCardShape
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.first(): ListItemShapes = ListItemDefaults.segmentedShapes(0, 2)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.item(): ListItemShapes = ListItemDefaults.segmentedShapes(1, 3)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.last(): ListItemShapes = ListItemDefaults.segmentedShapes(1, 2)

@Composable
public fun ListItemDefaults.segmentedShapes2(
    index: Int,
    count: Int,
): ListItemShapes {
    if (count == 1) {
        return ListItemDefaults.single()
    } else {
        return ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.single(): ListItemShapes {
    val first = first()
    val last = last()
    val firstShape = first.shape
    val lastShape = last.shape
    if (firstShape is CornerBasedShape && lastShape is CornerBasedShape) {
        return first.copy(
            shape =
                firstShape.copy(
                    bottomStart = lastShape.bottomStart,
                    bottomEnd = lastShape.bottomEnd,
                ),
        )
    } else {
        return ListItemDefaults.segmentedShapes(0, 1)
    }
}
