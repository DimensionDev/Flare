package dev.dimension.flare.ui.presenter.status

public object StatusShareImageLimits {
    // shortcut: bound raster allocation to 48 MB; revisit after device memory profiling.
    public fun canRender(
        width: Int,
        height: Int,
    ): Boolean = width in 1..16_384 && height in 1..16_384 && width.toLong() * height <= 12_000_000
}
