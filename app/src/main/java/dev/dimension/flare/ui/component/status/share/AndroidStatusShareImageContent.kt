package dev.dimension.flare.ui.component.status.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.UiTimelineV2

internal val AndroidStatusShareCaptureWidth: Dp = 360.dp + 64.dp * 2

@Composable
internal fun AndroidStatusShareImageContent(
    statusKey: MicroBlogKey,
    status: UiTimelineV2?,
    modifier: Modifier = Modifier,
    blockInteractions: Boolean = false,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(64.dp)
                    .width(360.dp)
                    .captureableShadow(
                        cornerRadius = 12.dp,
                        shadowRadius = 16.dp,
                    ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides
                        LocalTimelineAppearance.current.withSharePreviewDefaults(),
                ) {
                    StatusItem(
                        item = status,
                        detailStatusKey = statusKey,
                    )
                }
                if (blockInteractions) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                    )
                }
            }
        }
    }
}

private fun Modifier.captureableShadow(
    color: Color = Color.Black,
    cornerRadius: Dp = 0.dp,
    shadowRadius: Dp = 8.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    alpha: Float = 0.2f,
) = drawBehind {
    val shadowColor = color.copy(alpha = alpha).toArgb()
    val transparentColor = color.copy(alpha = 0f).toArgb()

    drawIntoCanvas {
        val paint = Paint()
        val frameworkPaint = paint.nativePaint
        frameworkPaint.color = transparentColor
        frameworkPaint.setShadowLayer(
            shadowRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            shadowColor,
        )
        it.drawRoundRect(
            0f,
            0f,
            size.width,
            size.height,
            cornerRadius.toPx(),
            cornerRadius.toPx(),
            paint,
        )
    }
}
