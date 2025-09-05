package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

public data class UiRichText(
    val data: Element,
    val isRtl: Boolean,
) {
    public val innerText: String = data.wholeText()
    val raw: String = data.wholeText()
    val html: String = data.html()
    public val isEmpty: Boolean = raw.isEmpty() && data.getAllElements().size <= 1
    public val isLongText: Boolean = innerText.length > 480
}

internal fun Element.toUi(): UiRichText =
    UiRichText(
        data = this,
        isRtl = text().isRtl(),
    )

internal fun parseHtml(html: String): Element = Ksoup.parse(html).body()

internal expect fun String.isRtl(): Boolean
