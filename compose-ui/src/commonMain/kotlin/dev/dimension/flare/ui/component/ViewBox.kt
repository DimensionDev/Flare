package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
public fun ViewBox(
    modifier: Modifier = Modifier,
    stretch: Boolean = true,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val measurable =
            measurables.firstOrNull()
                ?: return@Layout layout(
                    constraints.minWidth,
                    constraints.minHeight,
                ) {}

        val placeable = measurable.measure(Constraints(maxWidth = 360.dp.roundToPx()))
        val constraintWidth =
            if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val constraintHeight =
            if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        val scaleX = constraintWidth.toFloat() / placeable.width
        val scaleY = constraintHeight.toFloat() / placeable.height
        var scale = min(scaleX, scaleY)
        if (!stretch && scale > 1f) {
            scale = 1f
        }
        val targetWidth = (placeable.width * scale).toInt()
        val targetHeight = (placeable.height * scale).toInt()

        layout(targetWidth, targetHeight) {
            placeable.placeWithLayer(
                x = (targetWidth - placeable.width) / 2,
                y = (targetHeight - placeable.height) / 2,
            ) {
                this.scaleX = scale
                this.scaleY = scale
            }
        }
    }
}
