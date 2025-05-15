package dev.dimension.flare.ui.render

import androidx.compose.ui.unit.LayoutDirection
import com.fleeksoft.ksoup.nodes.Element
import java.text.Bidi

public actual data class UiRichText(
    val data: Element,
    val direction: LayoutDirection,
) {
    public val innerText: String = data.text()
    actual val raw: String = data.text()
    val html: String = data.html()
    public val isEmpty: Boolean = raw.isEmpty() && data.getAllElements().size <= 1
    public val isLongText: Boolean = innerText.length > 480
}

internal actual fun Element.toUi(): UiRichText =
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
