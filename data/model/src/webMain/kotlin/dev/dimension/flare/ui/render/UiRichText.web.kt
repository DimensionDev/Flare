package dev.dimension.flare.ui.render

import kotlinx.collections.immutable.ImmutableList

public actual typealias PlatformText = Unit

public actual fun renderPlatformText(renderRuns: ImmutableList<RenderContent>): PlatformText = Unit

public actual fun String.isRtl(): Boolean = false
