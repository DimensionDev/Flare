package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
public fun Modifier.compatClip(shape: Shape): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    return this.drawWithContent {
        val outline = shape.createOutline(size, layoutDirection, density)

        val clipPath =
            when (outline) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }

        clipPath(path = clipPath) {
            this@drawWithContent.drawContent()
        }
    }
}
