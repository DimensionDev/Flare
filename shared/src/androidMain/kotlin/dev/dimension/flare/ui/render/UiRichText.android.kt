package dev.dimension.flare.ui.render

import androidx.compose.ui.unit.LayoutDirection
import moe.tlaster.ktml.dom.Element
import java.text.Bidi

actual data class UiRichText(
    val data: Element,
    val direction: LayoutDirection,
) {
    val innerText = data.innerText
}

actual fun Element.toUi(): UiRichText =
    UiRichText(
        data = this,
        direction =
            if (Bidi(
                    innerText,
                    Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                ).baseIsLeftToRight()
            ) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            },
    )
