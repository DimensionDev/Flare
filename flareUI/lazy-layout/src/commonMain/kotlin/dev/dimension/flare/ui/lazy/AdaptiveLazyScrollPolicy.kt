package dev.dimension.flare.ui.lazy

import kotlin.math.abs

internal fun needsAdaptiveLazyScrollCorrection(
    current: Double,
    target: Double,
    tolerance: Double = 0.5,
): Boolean = abs(current - target) > tolerance
