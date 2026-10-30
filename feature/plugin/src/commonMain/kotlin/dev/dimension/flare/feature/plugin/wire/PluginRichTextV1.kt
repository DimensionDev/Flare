package dev.dimension.flare.feature.plugin.wire

import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.ui.render.parseHtml
import io.ktor.http.URLProtocol
import io.ktor.http.Url

internal fun RichTextV1.validatedHtml(baseUri: String = "https://plugin.invalid/"): Element {
    val body = parseHtml(value, baseUri)
    body.getAllElements().forEach { element ->
        val attribute =
            when (element.tagName().lowercase()) {
                "a" -> "href"
                "img" -> "src"
                "emoji" -> "target"
                else -> return@forEach
            }
        if (!element.hasAttr(attribute)) return@forEach
        val absolute = element.absUrl(attribute)
        require(absolute.length in 1..8_192) { "Invalid rich-text URL" }
        val url = Url(absolute)
        require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() && url.user == null && url.password == null) {
            "Rich-text URLs must use HTTPS without credentials"
        }
        element.attr(attribute, absolute)
    }
    return body
}
