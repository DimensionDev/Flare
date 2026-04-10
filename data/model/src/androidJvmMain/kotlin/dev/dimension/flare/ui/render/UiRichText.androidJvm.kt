package dev.dimension.flare.ui.render

import kotlinx.collections.immutable.ImmutableList
import java.text.Bidi

public actual typealias PlatformText = Unit

// Android/JVM does not use this
public actual fun renderPlatformText(renderRuns: ImmutableList<RenderContent>): PlatformText = Unit

public actual fun String.isRtl(): Boolean = !Bidi(this, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
