package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

expect class UiRichText {
    val raw: String
}

expect fun Element.toUi(): UiRichText

internal fun parseHtml(html: String): Element = Ksoup.parse(html).body()
