package dev.dimension.flare.ui.model

import kotlin.math.max
import kotlin.math.min

public data class TimelineCarouselLayoutSpec(
    public val widthMultiplier: Float,
    public val spacingMultiplier: Float,
    public val maxItemWidthMultiplier: Float = TimelineCarouselLayout.MAX_ITEM_WIDTH_MULTIPLIER,
) {
    public fun heightConstant(
        horizontalInsets: Float,
        itemSpacing: Float,
    ): Float =
        -max(horizontalInsets, 0f) * widthMultiplier +
            max(itemSpacing, 0f) * spacingMultiplier

    public fun height(
        viewportWidth: Float,
        horizontalInsets: Float,
        itemSpacing: Float,
    ): Float =
        max(
            max(viewportWidth, 0f) * widthMultiplier +
                heightConstant(horizontalInsets, itemSpacing),
            0f,
        )

    public fun itemWidth(
        contentWidth: Float,
        height: Float,
        aspectRatio: Float,
    ): Float {
        val safeContentWidth = max(contentWidth, 0f)
        val safeHeight = max(height, 0f)
        val safeAspectRatio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        return min(
            safeHeight * safeAspectRatio,
            safeContentWidth * maxItemWidthMultiplier,
        )
    }
}

public object TimelineCarouselLayout {
    public const val DEFAULT_HEIGHT_WIDTH_MULTIPLIER: Float = 10f / 16f
    public const val WIDE_HEIGHT_WIDTH_MULTIPLIER: Float = 0.68f
    public const val NEXT_ITEM_VISIBLE_FRACTION: Float = 0.67f
    public const val MAX_ITEM_WIDTH_MULTIPLIER: Float = 0.8f

    public fun spec(
        mediaCount: Int,
        firstAspectRatio: Float,
        secondAspectRatio: Float,
    ): TimelineCarouselLayoutSpec {
        if (
            mediaCount < 2 ||
            !firstAspectRatio.isValidAspectRatio() ||
            !secondAspectRatio.isValidAspectRatio()
        ) {
            return TimelineCarouselLayoutSpec(
                widthMultiplier = DEFAULT_HEIGHT_WIDTH_MULTIPLIER,
                spacingMultiplier = 0f,
            )
        }

        if (firstAspectRatio > 1f && secondAspectRatio > 1f) {
            return TimelineCarouselLayoutSpec(
                widthMultiplier = WIDE_HEIGHT_WIDTH_MULTIPLIER,
                spacingMultiplier = 0f,
            )
        }

        val secondVisibleFraction =
            if (mediaCount == 2) {
                1f
            } else {
                NEXT_ITEM_VISIBLE_FRACTION
            }
        val denominator = firstAspectRatio + secondVisibleFraction * secondAspectRatio
        return TimelineCarouselLayoutSpec(
            widthMultiplier = 1f / denominator,
            spacingMultiplier = -1f / denominator,
        )
    }

    private fun Float.isValidAspectRatio(): Boolean = isFinite() && this > 0f
}
