package dev.dimension.flare.ui.render

import androidx.compose.ui.unit.LayoutDirection
import com.fleeksoft.ksoup.nodes.Element
import java.text.Bidi

actual data class UiRichText(
    val data: Element,
    val direction: LayoutDirection,
) {
    val innerText = data.text()
    actual val raw: String = data.text()
}

actual fun Element.toUi(): UiRichText =
    UiRichText(
        data = this,
        direction =
            if (Bidi(
                    text(),
                    Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                ).baseIsLeftToRight()
            ) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            },
    )
