package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

actual data class UiRichText(
    val markdown: String,
    actual val raw: String,
    actual val data: Element,
    actual val isRTL: Boolean = false,
    actual val innerText: String = raw,
)

actual fun Element.toUi(): UiRichText =
    UiRichText(
        markdown = toMarkdown(),
        raw = text(),
        data = this,
    )

internal fun Node.toMarkdown(): String =
    when (this) {
        is Element -> toMarkdown()
        is TextNode ->
            text()
                .replace("#", "\\#")
        else -> ""
    }

internal fun Element.toMarkdown(): String =
    when (tagName().lowercase()) {
        "p" -> {
            val content = childNodes().joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "$content\n\n"
            }
        }
        "br" -> "<br />" // MarkdownUI support this tag
        "a" -> {
            val content = childNodes().joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                val href = attribute("href")?.value
                if (href.isNullOrBlank()) {
                    content
                } else {
                    "[$content]($href)\n"
                }
            }
        }
        "strong" -> {
            val content = childNodes().joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "**$content**"
            }
        }
        "em" -> {
            val content = childNodes().joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "*$content*"
            }
        }
        "code" -> {
            val content = childNodes().joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "`$content`"
            }
        }
        "pre" -> {
            val content = childNodes().joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "```\n$content```\n"
            }
        }
        "blockquote" -> {
            val content = childNodes().joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "> $content\n"
            }
        }
        "img" -> {
            val src = attribute("src")?.value
            if (src.isNullOrBlank()) {
                ""
            } else {
                "![]($src)\n"
            }
        }
        "span" -> {
            val content = childNodes().joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                content
            }
        }
        else -> {
            childNodes().joinToString("") { it.toMarkdown() }
        }
    }
