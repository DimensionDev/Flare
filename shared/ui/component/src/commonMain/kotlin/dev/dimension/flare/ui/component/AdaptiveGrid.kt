package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
internal fun AdaptiveGrid(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    expandedSize: Boolean = true,
) {
    Layout(
        modifier = modifier,
        content = content,
        measurePolicy = { measurables, constraints ->
            if (measurables.size == 1) {
                val placeables =
                    measurables.map {
                        if (expandedSize) {
                            it.measure(constraints)
                        } else {
                            it.measure(constraints.copy(maxHeight = constraints.maxWidth * 9 / 16))
                        }
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
                val rowCount =
                    ceil(sqrt(measurables.size.toDouble())).toInt().coerceIn(minimumValue = 1, maximumValue = 3)
                val firstColumnCount = measurables.size.mod(rowCount)

                val columnCount =
                    ceil((measurables.size - firstColumnCount).toDouble() / rowCount)
                        .toInt()
                        .coerceAtLeast(1)
                        .let {
                            if (firstColumnCount > 0) {
                                it + 1
                            } else {
                                it
                            }
                        }

                val itemSize = (constraints.maxWidth - space * (rowCount - 1)) / rowCount
                val itemConstraints =
                    constraints.copy(
                        maxWidth = itemSize,
                        maxHeight = itemSize,
                    )
                val firstColumnConstraints =
                    constraints.copy(
                        maxWidth =
                            if (firstColumnCount > 0) {
                                (constraints.maxWidth - space * (firstColumnCount - 1)) / firstColumnCount
                            } else {
                                itemSize
                            },
                        maxHeight = itemSize,
                    )
                val placeables =
                    measurables.mapIndexed { index, it ->
                        if (index < firstColumnCount) {
                            it.measure(firstColumnConstraints)
                        } else {
                            it.measure(itemConstraints)
                        }
                    }
                layout(
                    width = constraints.maxWidth,
                    height = (columnCount * itemSize + (columnCount - 1) * space),
                ) {
                    var row = 0
                    var column = 0
                    placeables.forEachIndexed { index, placeable ->
                        if (index < firstColumnCount) {
                            placeable.placeRelative(
                                x = row * firstColumnConstraints.maxWidth + row * space,
                                y = column * itemSize + column * space,
                            )
                            row++
                            if (row == firstColumnCount) {
                                row = 0
                                column++
                            }
                        } else {
                            placeable.placeRelative(
                                x = row * itemSize + row * space,
                                y = column * itemSize + column * space,
                            )
                            row++
                            if (row == rowCount) {
                                row = 0
                                column++
                            }
                        }
                    }
                }
            }
        },
    )
}
