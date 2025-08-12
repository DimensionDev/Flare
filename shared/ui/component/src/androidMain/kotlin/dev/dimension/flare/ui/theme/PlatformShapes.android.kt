package dev.dimension.flare.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

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

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    actual val topCardShape: Shape
        @Composable
        get() =
            listCardItemShape.copy(
                topStart = listCardContainerShape.topStart,
                topEnd = listCardContainerShape.topEnd,
            )

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    actual val bottomCardShape: Shape
        @Composable
        get() =
            listCardItemShape.copy(
                bottomStart = listCardContainerShape.bottomStart,
                bottomEnd = listCardContainerShape.bottomEnd,
            )

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    actual val listCardContainerShape: CornerBasedShape
        @Composable
        get() = MaterialTheme.shapes.largeIncreased

    actual val listCardItemShape: CornerBasedShape
        @Composable
        get() = MaterialTheme.shapes.extraSmall
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

@Composable
public fun Modifier.listCardContainer(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this.clip(PlatformShapes.listCardContainerShape)
    } else {
        this.compatClip(PlatformShapes.listCardContainerShape)
    }

@Composable
public fun Modifier.listCardItem(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this.clip(PlatformShapes.listCardItemShape)
    } else {
        this.compatClip(PlatformShapes.listCardItemShape)
    }

@Composable
private fun Modifier.compatClip(shape: Shape): Modifier {
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
