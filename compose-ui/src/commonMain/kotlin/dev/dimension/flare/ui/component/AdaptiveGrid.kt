package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AdaptiveGrid(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    expandedSize: Boolean = true,
) {
    Layout(
        content = content,
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            if (measurables.size == 1) {
                val placeable =
                    if (expandedSize) {
                        measurables[0].measure(
                            constraints.copy(
                                maxHeight = constraints.maxWidth * 16 / 9,
                            ),
                        )
                    } else {
                        measurables[0].measure(
                            constraints.copy(maxHeight = constraints.maxWidth * 9 / 16),
                        )
                    }
                return@Layout layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }

            val gapPx = spacing.roundToPx()
            if (measurables.size in 2..4) {
                val width = constraints.maxWidth
                val height = (width * 9) / 16
                val containerHeight =
                    if (expandedSize) {
                        height.coerceIn(constraints.minHeight, constraints.maxHeight)
                    } else {
                        height
                    }

                val placeables =
                    when (measurables.size) {
                        2 -> {
                            val childWidth = (width - gapPx) / 2
                            measurables.map { child ->
                                child.measure(
                                    constraints.copy(
                                        maxWidth = childWidth,
                                        maxHeight = containerHeight,
                                    ),
                                )
                            }
                        }

                        3 -> {
                            val leftWidth = (width - gapPx) / 2
                            val rightWidth = width - leftWidth - gapPx
                            val rightHeight = (containerHeight - gapPx) / 2

                            measurables.mapIndexed { index, measurable ->
                                when (index) {
                                    0 ->
                                        measurable.measure(
                                            constraints.copy(
                                                maxWidth = leftWidth,
                                                maxHeight = containerHeight,
                                            ),
                                        )

                                    else ->
                                        measurable.measure(
                                            constraints.copy(
                                                maxWidth = rightWidth,
                                                maxHeight = rightHeight,
                                            ),
                                        )
                                }
                            }
                        }

                        else -> {
                            val cellW = (width - gapPx) / 2
                            val cellH = (containerHeight - gapPx) / 2
                            measurables.map { child ->
                                child.measure(
                                    constraints.copy(
                                        maxWidth = cellW,
                                        maxHeight = cellH,
                                    ),
                                )
                            }
                        }
                    }

                return@Layout layout(width, containerHeight) {
                    when (measurables.size) {
                        2 -> {
                            placeables[0].placeRelative(0, 0)
                            placeables[1].placeRelative(placeables[0].width + gapPx, 0)
                        }

                        3 -> {
                            placeables[0].placeRelative(0, 0)
                            val xRight = placeables[0].width + gapPx
                            placeables[1].placeRelative(xRight, 0)
                            placeables[2].placeRelative(
                                xRight,
                                placeables[1].height + gapPx,
                            )
                        }

                        4 -> {
                            val cellW = placeables[0].width
                            val cellH = placeables[0].height
                            placeables.forEachIndexed { index, p ->
                                val col = index % 2
                                val row = index / 2
                                p.placeRelative(
                                    x = col * (cellW + gapPx),
                                    y = row * (cellH + gapPx),
                                )
                            }
                        }
                    }
                }
            }

            val maxColumns = 3
            val columns = maxColumns
            val itemSize = (constraints.maxWidth - gapPx * (columns - 1)) / columns
            val rows = (measurables.size + columns - 1) / columns

            val lastRowCount = measurables.size - (rows - 1) * columns
            val lastRowFlexible = lastRowCount != columns

            val placeables =
                measurables.mapIndexed { index, measurable ->
                    val isInLastRow = index / columns == rows - 1
                    val isFlexible = lastRowFlexible && isInLastRow
                    val childWidth =
                        if (isFlexible) {
                            (constraints.maxWidth - gapPx * (lastRowCount - 1)) / lastRowCount
                        } else {
                            itemSize
                        }

                    measurable.measure(
                        constraints.copy(
                            maxWidth = childWidth,
                            maxHeight = itemSize,
                        ),
                    )
                }

            val totalHeight = rows * itemSize + gapPx * (rows - 1)

            layout(constraints.maxWidth, totalHeight) {
                placeables.forEachIndexed { index, p ->
                    val row = index / columns
                    val col = index % columns
                    val isLastRow = row == rows - 1 && lastRowFlexible
                    val effectiveColumns = if (isLastRow) lastRowCount else columns
                    val cellW =
                        if (isLastRow) {
                            (constraints.maxWidth - gapPx * (effectiveColumns - 1)) / effectiveColumns
                        } else {
                            itemSize
                        }

                    p.placeRelative(
                        x = col * (cellW + gapPx),
                        y = row * (itemSize + gapPx),
                    )
                }
            }
        },
    )
}
