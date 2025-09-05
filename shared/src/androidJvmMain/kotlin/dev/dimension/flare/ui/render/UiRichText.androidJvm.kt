package dev.dimension.flare.ui.render

import java.text.Bidi

internal actual fun String.isRtl(): Boolean = !Bidi(this, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
