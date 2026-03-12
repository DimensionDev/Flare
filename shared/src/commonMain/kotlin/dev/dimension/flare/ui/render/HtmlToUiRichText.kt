package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

internal fun Element.toUi(): UiRichText {
    val result = mapHtmlToRenderResult(this)
    val innerText = wholeText()
    return UiRichText(
        renderRuns = result.contents,
        isRtl = innerText.isRtl(),
        raw = result.raw,
        innerText = innerText,
        imageUrls = result.imageUrls,
    )
}

internal fun parseHtml(html: String): Element = Ksoup.parse(html).body()
