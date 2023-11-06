package dev.dimension.flare.ui.model

import moe.tlaster.ktml.dom.*

actual class UiUserExtra(
    val nameMarkdown: String,
    val descriptionMarkdown: String?,
)

internal actual fun createUiUserExtra(user: UiUser): UiUserExtra {
    return UiUserExtra(
        nameMarkdown = when (user) {
            is UiUser.Mastodon -> user.nameElement.toMarkdown()
            is UiUser.Misskey -> user.nameElement.toMarkdown()
            is UiUser.Bluesky -> user.nameElement.toMarkdown()
        },
        descriptionMarkdown = when (user) {
            is UiUser.Mastodon -> user.descriptionElement?.toMarkdown()
            is UiUser.Misskey -> user.descriptionElement?.toMarkdown()
            is UiUser.Bluesky -> user.descriptionElement?.toMarkdown()
        },
    )
}

internal fun Node.toMarkdown(): String {
    return when (this) {
        is Comment -> ""
        is Doctype -> ""
        is Element -> toMarkdown()
        is Text -> text
    }
}

internal fun Element.toMarkdown(): String {
    return when (name) {
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
}