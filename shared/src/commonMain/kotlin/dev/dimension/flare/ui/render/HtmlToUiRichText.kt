package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

public fun Element.toUi(): UiRichText =
    UiRichText(
        renderRuns = mapHtmlToRenderContents(this),
        isRtl = text().isRtl(),
        raw = rawText(),
        innerText = wholeText(),
        imageUrls = imageUrls(),
    )

public fun parseHtml(html: String): Element = Ksoup.parse(html).body()

private fun Element.rawText(): String =
    nodeStream()
        .joinToString("") { node ->
            when {
                node is TextNode -> node.getWholeText()
                node.nameIs("br") -> "\n"
                node.nameIs("img") -> node.attr("alt")
                node.nameIs("emoji") -> node.attr("alt")
                else -> ""
            }
        }

private fun Element.imageUrls(): ImmutableList<String> =
    getElementsByTag("img")
        .mapNotNull { it.attr("src").ifEmpty { null } }
        .plus(
            getElementsByTag("emoji")
                .mapNotNull { it.attr("target").ifEmpty { null } },
        ).toImmutableList()
