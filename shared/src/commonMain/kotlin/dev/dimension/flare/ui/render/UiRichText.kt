package dev.dimension.flare.ui.render

import androidx.compose.runtime.Immutable
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import de.cketti.codepoints.codePointCount

@Immutable
public data class UiRichText(
    val data: Element,
    val isRtl: Boolean,
) {
    public val innerText: String = data.wholeText()
    val raw: String by lazy {
        data
            .nodeStream()
            .map { node ->
                when {
                    node is TextNode -> node.getWholeText()
                    node.nameIs("br") -> "\n"
                    node.nameIs("img") -> node.attr("alt")
                    node.nameIs("emoji") -> node.attr("alt")
                    else -> ""
                }
            }.joinToString("")
    }
    val html: String = data.html()
    public val isEmpty: Boolean = raw.isEmpty() && data.getAllElements().size <= 1
    public val isLongText: Boolean = innerText.codePointCount() > 480
}

internal fun Element.toUi(): UiRichText =
    UiRichText(
        data = this,
        isRtl = text().isRtl(),
    )

internal fun parseHtml(html: String): Element = Ksoup.parse(html).body()

internal expect fun String.isRtl(): Boolean
