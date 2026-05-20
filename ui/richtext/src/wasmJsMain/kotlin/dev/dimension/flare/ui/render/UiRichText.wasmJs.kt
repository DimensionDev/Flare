package dev.dimension.flare.ui.render

import kotlinx.collections.immutable.ImmutableList

public actual typealias PlatformText = Unit

internal actual fun renderPlatformText(renderRuns: ImmutableList<RenderContent>): PlatformText = Unit

internal actual fun String.isRtl(): Boolean =
    any { char ->
        char in '\u0590'..'\u08FF' ||
            char in '\uFB1D'..'\uFDFF' ||
            char in '\uFE70'..'\uFEFF'
    }
