package dev.dimension.flare.ui.render

import moe.tlaster.ktml.dom.Comment
import moe.tlaster.ktml.dom.Doctype
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Node
import moe.tlaster.ktml.dom.Text

typealias UiRichText = String

actual fun Element.toUi(): UiRichText = toMarkdown()

internal fun Node.toMarkdown(): String =
    when (this) {
        is Comment -> ""
        is Doctype -> ""
        is Element -> toMarkdown()
        is Text -> text
    }

internal fun Element.toMarkdown(): String =
    when (name) {
        "p" -> {
            val content = children.joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "$content\n\n"
            }
        }
        "br" -> "\n"
        "a" -> {
            val content = children.joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "[$content](${attributes["href"]})\n"
            }
        }
        "strong" -> {
            val content = children.joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "**$content**"
            }
        }
        "em" -> {
            val content = children.joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "*$content*"
            }
        }
        "code" -> {
            val content = children.joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "`$content`"
            }
        }
        "pre" -> {
            val content = children.joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "```\n$content```\n"
            }
        }
        "blockquote" -> {
            val content = children.joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                "> $content\n"
            }
        }
        "img" -> {
            "![](${attributes["src"]})\n"
        }
        "span" -> {
            val content = children.joinToString("") { it.toMarkdown() }
            if (content.isBlank()) {
                ""
            } else {
                content
            }
        }
        else -> {
            children.joinToString("") { it.toMarkdown() }
        }
    }
