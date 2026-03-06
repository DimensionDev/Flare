package dev.dimension.flare.ui.render

import java.text.Bidi

public actual typealias PlatformText = Unit

// Android/JVM does not use this
internal actual fun UiRichText.renderPlatformText(): PlatformText = Unit

internal actual fun String.isRtl(): Boolean = !Bidi(this, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
