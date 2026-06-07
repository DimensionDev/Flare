package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import dev.dimension.flare.ui.theme.PlatformShapes
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
public fun Modifier.listCard(
    index: Int = 0,
    totalCount: Int = 0,
): Modifier = this.clip(listCardShape(index, totalCount))

@Composable
public fun listCardShape(
    index: Int = 0,
    totalCount: Int = 0,
): Shape =
    if (totalCount > 1) {
        // if first
        if (index == 0) {
            PlatformTheme.shapes.topCardShape
        } else if (index == totalCount - 1) {
            PlatformTheme.shapes.bottomCardShape
        } else {
            PlatformTheme.shapes.listCardItemShape
        }
    } else {
        PlatformTheme.shapes.listCardContainerShape
    }

@Composable
public fun Modifier.listCardContainer(): Modifier = this.clip(PlatformShapes.listCardContainerShape)

@Composable
public fun Modifier.listCardItem(): Modifier = this.clip(PlatformShapes.listCardItemShape)
