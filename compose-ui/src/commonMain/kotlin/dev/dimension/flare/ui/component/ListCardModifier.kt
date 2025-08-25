package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
public fun Modifier.listCard(
    index: Int = 0,
    totalCount: Int = 0,
): Modifier =
    if (totalCount > 1) {
        // if first
        if (index == 0) {
            clip(
                shape = PlatformTheme.shapes.topCardShape,
            )
        } else if (index == totalCount - 1) {
            clip(
                shape = PlatformTheme.shapes.bottomCardShape,
            )
        } else {
            clip(shape = PlatformTheme.shapes.listCardItemShape)
        }
    } else {
        clip(shape = PlatformTheme.shapes.listCardContainerShape)
    }
