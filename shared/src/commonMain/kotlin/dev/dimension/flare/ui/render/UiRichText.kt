package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

public expect class UiRichText {
    internal val raw: String
}

internal expect fun Element.toUi(): UiRichText

internal fun parseHtml(html: String): Element = Ksoup.parse(html).body()
