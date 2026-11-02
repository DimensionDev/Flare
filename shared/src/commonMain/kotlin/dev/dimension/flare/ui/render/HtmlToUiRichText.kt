package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.ui.model.UiMedia
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun Element.toUi(
    sourceLanguages: List<String> = emptyList(),
    imageLinks: Map<String, UiMedia.Image> = emptyMap(),
): UiRichText {
    val result = mapHtmlToRenderResult(this, imageLinks)
    val innerText = wholeText()
    return UiRichText(
        renderRuns = result.contents,
        isRtl = innerText.resolveRtl(sourceLanguages),
        raw = result.raw,
        innerText = innerText,
        imageUrls = result.imageUrls,
    )
}

@HiddenFromObjC
public fun parseHtml(
    html: String,
    baseUri: String = "",
): Element = Ksoup.parse(html, baseUri).body()
