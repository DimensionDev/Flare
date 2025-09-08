package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal actual fun PlatformCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    shape: Shape?,
    elevated: Boolean,
    containerColor: Color?,
    content: @Composable (() -> Unit)
) {
    Box(
        modifier = modifier
            .let {
                if (shape != null) {
                    it.clip(shape)
                } else {
                    it.clip(PlatformTheme.shapes.medium)
                }
            }
            .let {
                if (containerColor != null) {
                    it.background(containerColor)
                } else {
                    it
                }
            }
            .let {
                if (onClick != null) {
                    it.clickable {
                        onClick.invoke()
                    }
                } else {
                    it
                }
            }
    ) {
        content.invoke()
    }
}