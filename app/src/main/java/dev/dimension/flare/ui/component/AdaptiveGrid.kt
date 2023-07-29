package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
fun AdaptiveGrid(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
) {
    Layout(
        modifier = modifier,
        content = content,
        measurePolicy = { measurables, constraints ->
            if (measurables.size == 1) {
                val placeables = measurables.map {
                    it.measure(constraints)
                }
                layout(
                    width = placeables[0].width,
                    height = placeables[0].height,
                ) {
                    placeables.forEach { placeable ->
                        placeable.place(
                            x = 0,
                            y = 0,
                        )
                    }
                }

            } else {
                val space = spacing.toPx().toInt()
                val columns = ceil(sqrt(measurables.size.toDouble())).toInt().coerceAtLeast(1)
                val rows = ceil(measurables.size.toDouble() / columns)
                val itemSize = constraints.maxWidth / columns - space
                val itemConstraints = constraints.copy(
                    minWidth = itemSize,
                    maxWidth = itemSize,
                    minHeight = itemSize,
                    maxHeight = itemSize,
                )
                val placeables = measurables.map {
                    it.measure(itemConstraints)
                }
                layout(
                    width = constraints.maxWidth,
                    height = (rows * itemSize).toInt(),
                ) {
                    var row = 0
                    var column = 0
                    placeables.forEach { placeable ->
                        placeable.place(
                            x = column * itemSize + column * space,
                            y = row * itemSize + row * space,
                        )
                        column++
                        if (column == columns) {
                            column = 0
                            row++
                        }
                    }
                }
            }
        }
    )
}