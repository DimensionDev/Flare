package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

public actual data class UiRichText internal constructor(
    val html: String,
    actual val raw: String,
)

internal actual fun Element.toUi(): UiRichText =
    UiRichText(
        html = toPango(),
        raw = text(),
    )

private fun Node.toPango(): String =
    when (this) {
        is Element -> toPango()
        is TextNode ->
            text().replaceXMLSpecialChars()
        else -> ""
    }

private fun Element.toPango(): String =
    when (tagName().lowercase()) {
        "p" -> {
            val content = childNodes().joinToString("") { it.toPango() }
            if (content.isBlank()) {
                ""
            } else {
                "$content\n"
            }
        }
//        "br" -> "<span allow_breaks=\"true\"> \n </span>"
        "a" -> {
            val content = childNodes().joinToString("") { it.toPango() }
            if (content.isBlank()) {
                ""
            } else {
                val href = attribute("href")?.value
                if (href.isNullOrBlank()) {
                    content
                } else {
                    "<a href=\"${href}\">$content</a>"
                }
            }
        }
        "strong" -> {
            val content = childNodes().joinToString("") { it.toPango() }
            if (content.isBlank()) {
                ""
            } else {
                "<big>$content</big>"
            }
        }
        "em" -> {
            val content = childNodes().joinToString("") { it.toPango() }
            if (content.isBlank()) {
                ""
            } else {
                "<i>$content</i>"
            }
        }
//        "code" -> {
//            val content = childNodes().joinToString("") { it.toPango() }
//            if (content.isBlank()) {
//                ""
//            } else {
//                "`$content`"
//            }
//        }
//        "pre" -> {
//            val content = childNodes().joinToString("") { it.toPango() }
//            if (content.isBlank()) {
//                ""
//            } else {
//                "```\n$content```\n"
//            }
//        }
//        "blockquote" -> {
//            val content = childNodes().joinToString("") { it.toPango() }
//            if (content.isBlank()) {
//                ""
//            } else {
//                "> $content\n"
//            }
//        }
        "img" -> {
            val src = attribute("src")?.value
            if (src.isNullOrBlank()) {
                ""
            } else {
                "<img src=\"$src\"/>"
            }
        }
        "span" -> {
            val content = childNodes().joinToString("") { it.toPango() }
            if (content.isBlank()) {
                ""
            } else {
                content
            }
        }
        else -> {
            childNodes().joinToString("") { it.toPango() }
        }
    }

private fun String.replaceXMLSpecialChars(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
