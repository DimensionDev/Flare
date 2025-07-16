package dev.dimension.flare.ui.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@Composable
internal fun Modifier.listCard(
    index: Int = 0,
    totalCount: Int = 0,
    itemShape: CornerBasedShape = MaterialTheme.shapes.extraSmall,
    containerShape: CornerBasedShape = MaterialTheme.shapes.medium,
): Modifier =
    if (totalCount > 1) {
        // if first
        if (index == 0) {
            clip(
                shape =
                    containerShape
                        .copy(
                            bottomStart = itemShape.bottomStart,
                            bottomEnd = itemShape.bottomEnd,
                        ),
            )
        } else if (index == totalCount - 1) {
            clip(
                shape =
                    containerShape
                        .copy(
                            topStart = itemShape.topStart,
                            topEnd = itemShape.topEnd,
                        ),
            )
        } else {
            clip(shape = itemShape)
        }
    } else {
        clip(shape = containerShape)
    }
