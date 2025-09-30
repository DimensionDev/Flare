package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

public data class UiRichText(
    val data: Element,
    val isRtl: Boolean,
) {
    public val innerText: String = data.wholeText()
    val raw: String = data.wholeText()
    val html: String = data.html()
    public val isEmpty: Boolean = raw.isEmpty() && data.getAllElements().size <= 1
    public val isLongText: Boolean = innerText.length > 480
    public val markdown: String by lazy {
        data.toMarkdown()
    }
}

internal fun Element.toUi(): UiRichText =
    UiRichText(
        data = this,
        isRtl = text().isRtl(),
    )

internal fun parseHtml(html: String): Element = Ksoup.parse(html).body()

internal expect fun String.isRtl(): Boolean

private fun Node.toMarkdown(): String =
    when (this) {
        is Element -> toMarkdown()
        is TextNode ->
            text()
                .escapeMarkdown()
        else -> ""
    }

private fun Element.toMarkdown(): String =
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
            content.ifBlank {
                ""
            }
        }
        else -> {
            childNodes().joinToString("") { it.toMarkdown() }
        }
    }

// /\\ (backslash itself)
// ` (backtick)
// * (asterisk)
// _ (underscore)
// { } (curly braces)
// [ ] (square brackets)
// ( ) (parentheses)
// # (hash mark)
// + (plus sign)
// - (minus sign or hyphen)
// . (dot)
// ! (exclamation mark)
// > (blockquote)
private fun String.escapeMarkdown(): String =
    replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("#", "\\#")
        .replace("+", "\\+")
        .replace("-", "\\-")
        .replace(".", "\\.")
        .replace("!", "\\!")
        .replace(">", "\\>")
