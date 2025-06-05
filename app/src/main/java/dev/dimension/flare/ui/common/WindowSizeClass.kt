package dev.dimension.flare.ui.common

import androidx.window.core.layout.WindowSizeClass

internal fun WindowSizeClass.isCompat(): Boolean = this.minWidthDp <= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND

internal fun WindowSizeClass.isNormal(): Boolean =
    this.minWidthDp in WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND..WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND

internal fun WindowSizeClass.isExpanded(): Boolean = this.minWidthDp >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
