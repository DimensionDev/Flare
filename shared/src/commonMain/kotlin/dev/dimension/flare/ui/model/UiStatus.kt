package dev.dimension.flare.ui.model

import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.mapper.resolveMisskeyEmoji
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Clock
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Node
import moe.tlaster.ktml.dom.Text
import moe.tlaster.mfm.parser.tree.BoldNode
import moe.tlaster.mfm.parser.tree.CashNode
import moe.tlaster.mfm.parser.tree.CenterNode
import moe.tlaster.mfm.parser.tree.CodeBlockNode
import moe.tlaster.mfm.parser.tree.EmojiCodeNode
import moe.tlaster.mfm.parser.tree.FnNode
import moe.tlaster.mfm.parser.tree.HashtagNode
import moe.tlaster.mfm.parser.tree.InlineCodeNode
import moe.tlaster.mfm.parser.tree.ItalicNode
import moe.tlaster.mfm.parser.tree.LinkNode
import moe.tlaster.mfm.parser.tree.MathBlockNode
import moe.tlaster.mfm.parser.tree.MathInlineNode
import moe.tlaster.mfm.parser.tree.MentionNode
import moe.tlaster.mfm.parser.tree.QuoteNode
import moe.tlaster.mfm.parser.tree.RootNode
import moe.tlaster.mfm.parser.tree.SearchNode
import moe.tlaster.mfm.parser.tree.SmallNode
import moe.tlaster.mfm.parser.tree.StrikeNode
import moe.tlaster.mfm.parser.tree.UrlNode
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken

internal fun List<Token>.toHtml(accountKey: MicroBlogKey): Element {
    val body = Element("body")
    forEach {
        body.children.add(it.toHtml(accountKey))
    }
    return body
}

private fun Token.toHtml(accountKey: MicroBlogKey): Node =
    when (this) {
        is CashTagToken ->
            Element("a").apply {
                attributes["href"] = AppDeepLink.Search(accountKey, value)
                children.add(Text(value))
            }
        // not supported
        is EmojiToken -> Text(value)
        is HashTagToken ->
            Element("a").apply {
                attributes["href"] = AppDeepLink.Search(accountKey, value)
                children.add(Text(value))
            }

        is StringToken -> Text(value)
        is UrlToken ->
            Element("a").apply {
                attributes["href"] = value
                children.add(Text(value.trimUrl()))
            }

        is UserNameToken ->
            Element("a").apply {
                attributes["href"] =
                    AppDeepLink.ProfileWithNameAndHost(accountKey, value, accountKey.host)
                children.add(Text(value))
            }
    }

internal fun moe.tlaster.mfm.parser.tree.Node.toHtml(accountKey: MicroBlogKey): Element =
    when (this) {
        is CenterNode -> {
            Element("center").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is CodeBlockNode -> {
            Element("pre").apply {
                children.add(
                    Element("code").apply {
                        language?.let { attributes["lang"] = it }
                        children.add(Text(code))
                    },
                )
            }
        }

        is MathBlockNode -> {
            Element("pre").apply {
                children.add(
                    Element("code").apply {
                        attributes["lang"] = "math"
                        children.add(Text(formula))
                    },
                )
            }
        }

        is QuoteNode -> {
            Element("blockquote").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is SearchNode -> {
            Element("search").apply {
                children.add(Text(query))
            }
        }

        is BoldNode -> {
            Element("strong").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is FnNode -> {
            Element("fn").apply {
                attributes["name"] = name
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is ItalicNode -> {
            Element("em").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is RootNode -> {
            Element("body").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is SmallNode -> {
            Element("small").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is StrikeNode -> {
            Element("s").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is CashNode -> {
            Element("a").apply {
                attributes["href"] = AppDeepLink.Search(accountKey, "$$content")
                children.add(Text("$$content"))
            }
        }

        is EmojiCodeNode -> {
            Element("img").apply {
                attributes["src"] = resolveMisskeyEmoji(emoji, accountKey.host)
                attributes["alt"] = emoji
            }
        }

        is HashtagNode -> {
            Element("a").apply {
                attributes["href"] = AppDeepLink.Search(accountKey, "#$tag")
                children.add(Text("#$tag"))
            }
        }

        is InlineCodeNode -> {
            Element("code").apply {
                children.add(Text(code))
            }
        }

        is LinkNode -> {
            Element("a").apply {
                attributes["href"] = url
                children.add(Text(content))
            }
        }

        is MathInlineNode -> {
            Element("code").apply {
                attributes["lang"] = "math"
                children.add(Text(formula))
            }
        }

        is MentionNode -> {
            Element("a").apply {
                val deeplink =
                    host?.let {
                        AppDeepLink.ProfileWithNameAndHost(accountKey, userName, it)
                    } ?: AppDeepLink.ProfileWithNameAndHost(accountKey, userName, accountKey.host)
                attributes["href"] = deeplink
                children.add(
                    Text(
                        buildString {
                            append("@")
                            append(userName)
                            if (host != null) {
                                append("@")
                                append(host)
                            }
                        },
                    ),
                )
            }
        }

        is moe.tlaster.mfm.parser.tree.TextNode -> {
            Element("span").apply {
                children.add(Text(content))
            }
        }

        is UrlNode -> {
            Element("a").apply {
                attributes["href"] = url
                children.add(Text(url.trimUrl()))
            }
        }
    }

private fun String.trimUrl(): String =
    this
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .removeSuffix("/")
        .let {
            if (it.length > 30) {
                it.substring(0, 30) + "..."
            } else {
                it
            }
        }

fun createSampleStatus(user: UiUserV2) =
    UiTimeline(
        topMessage = null,
        content =
            UiTimeline.ItemContent.Status(
                images = persistentListOf(),
                sensitive = false,
                contentWarning = null,
                user = user,
                quote = persistentListOf(),
                content =
                    Element("body")
                        .apply {
                            children.add(
                                Text(
                                    "Sample content for ${user.name} on ${user.key.host}",
                                ),
                            )
                        }.toUi(),
                actions = persistentListOf(),
                poll = null,
                statusKey = MicroBlogKey(id = "123", host = user.key.host),
                card = null,
                createdAt = Clock.System.now().toUi(),
                bottomContent = null,
                topEndContent = null,
                aboveTextContent = null,
                onClicked = {},
            ),
        platformType = PlatformType.Mastodon,
    )
