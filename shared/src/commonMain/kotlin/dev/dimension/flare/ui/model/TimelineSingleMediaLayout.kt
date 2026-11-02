package dev.dimension.flare.ui.model

public object TimelineSingleMediaLayout {
    public fun maximumHeight(viewportHeight: Float): Float =
        if (viewportHeight.isFinite() && viewportHeight > 0f) {
            minOf(viewportHeight * 0.5f, 420f)
        } else {
            420f
        }

    public fun aspectRatio(
        sourceAspectRatio: Float,
        cropTallMedia: Boolean,
    ): Float {
        val ratio = sourceAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        return if (cropTallMedia) maxOf(ratio, 9f / 21f) else ratio
    }

    public fun width(
        availableWidth: Float,
        aspectRatio: Float,
        maximumHeight: Float,
    ): Float {
        if (!availableWidth.isFinite() || availableWidth <= 0f || !maximumHeight.isFinite() || maximumHeight <= 0f) {
            return 0f
        }
        return minOf(availableWidth, maximumHeight * aspectRatio(aspectRatio, cropTallMedia = false))
    }
}
