package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.ktml.dom.Comment
import moe.tlaster.ktml.dom.Doctype
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Node
import moe.tlaster.ktml.dom.Text

actual class UiUserExtra(
    val nameMarkdown: String,
    val descriptionMarkdown: String?,
    val fieldsMarkdown: ImmutableListWrapper<Pair<String, String>>,
)

internal actual fun createUiUserExtra(user: UiUser): UiUserExtra {
    return UiUserExtra(
        nameMarkdown = user.nameElement.toMarkdown(),
        descriptionMarkdown = user.descriptionElement?.toMarkdown(),
        fieldsMarkdown =
            when (user) {
                is UiUser.Mastodon ->
                    user.fieldsParsed.mapValues { (_, value) ->
                        value.toMarkdown()
                    }
                is UiUser.Misskey ->
                    user.fieldsParsed.mapValues { (_, value) ->
                        value.toMarkdown()
                    }
                is UiUser.Bluesky -> persistentMapOf()
                is UiUser.XQT ->
                    user.fieldsParsed.mapValues { (_, value) ->
                        value.toMarkdown()
                    }
            }.map { (key, value) ->
                key to value
            }.toImmutableList().toImmutableListWrapper(),
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
