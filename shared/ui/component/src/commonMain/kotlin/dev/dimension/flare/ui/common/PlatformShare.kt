package dev.dimension.flare.ui.common

import coil3.PlatformContext

internal expect object PlatformShare {
    fun shareText(
        context: PlatformContext,
        text: String,
    )
}
