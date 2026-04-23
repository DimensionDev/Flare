package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun Element.toUi(sourceLanguages: List<String> = emptyList()): UiRichText {
    val result = mapHtmlToRenderResult(this)
    val innerText = wholeText()
    return UiRichText(
        renderRuns = result.contents,
        isRtl = innerText.resolveRtl(sourceLanguages),
        raw = result.raw,
        innerText = innerText,
        imageUrls = result.imageUrls,
    )
}

internal fun parseHtml(html: String): Element = Ksoup.parse(html).body()
