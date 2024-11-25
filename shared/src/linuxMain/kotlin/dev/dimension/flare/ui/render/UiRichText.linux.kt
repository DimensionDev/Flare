package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.nodes.Element

actual data class UiRichText(
    val html: String,
    actual val raw: String,
)

actual fun Element.toUi(): UiRichText =
    UiRichText(
        html = html(),
        raw = text(),
    )
