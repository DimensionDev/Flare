package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.characterDirectionForLanguage
import platform.NaturalLanguage.NLLanguageRecognizer

public actual data class UiRichText internal constructor(
    val markdown: String,
    actual val raw: String,
    val isRTL: Boolean = raw.isRightToLeft(),
)

@OptIn(BetaInteropApi::class)
private fun String.isRightToLeft(): Boolean =
    autoreleasepool {
        val langCode =
            NLLanguageRecognizer
                .dominantLanguageForString(this) ?: return@autoreleasepool false

        NSLocale.characterDirectionForLanguage(langCode) ==
            NSLocaleLanguageDirectionRightToLeft
    }

internal actual fun Element.toUi(): UiRichText =
    UiRichText(
        markdown = toMarkdown(),
        raw = text(),
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
