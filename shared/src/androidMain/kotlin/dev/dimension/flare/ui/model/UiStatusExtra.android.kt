package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.network.mastodon.api.model.Mention
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.MicroBlogKey
import java.text.Bidi
import moe.tlaster.mfm.parser.MFMParser
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

internal val misskeyParser by lazy {
    MFMParser()
}

@Immutable
actual data class UiStatusExtra(
    val contentElement: Element,
    val contentDirection: LayoutDirection
) {
    companion object {
        val Empty = UiStatusExtra(
            contentElement = Element("span"),
            contentDirection = LayoutDirection.Ltr,
        )
    }
}

val UiStatus.contentToken get() = extra.contentElement
val UiStatus.contentDirection get() = extra.contentDirection

internal actual fun createStatusExtra(status: UiStatus): UiStatusExtra {
    return when (status) {
        is UiStatus.Mastodon -> {
            UiStatusExtra(
                contentElement = parseContent(status.raw, status.accountKey.host),
                contentDirection = if (Bidi(
                        status.content,
                        Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
                    ).baseIsLeftToRight()
                ) {
                    LayoutDirection.Ltr
                } else {
                    LayoutDirection.Rtl
                },
            )
        }

        is UiStatus.MastodonNotification -> {
            UiStatusExtra.Empty
        }
        is UiStatus.Misskey -> {
            UiStatusExtra(
                contentElement = misskeyParser.parse(status.content).toHtml(status.accountKey.host),
                contentDirection = if (Bidi(
                        status.content,
                        Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
                    ).baseIsLeftToRight()
                ) {
                    LayoutDirection.Ltr
                } else {
                    LayoutDirection.Rtl
                },
            )
        }

        is UiStatus.MisskeyNotification -> {
            UiStatusExtra.Empty
        }
    }
}


private fun parseContent(
    status: Status,
    host: String,
): Element {
    val emoji = status.emojis.orEmpty()
    val mentions = status.mentions.orEmpty()
//    val tags = status.tags.orEmpty()
    var content = status.content.orEmpty()
    emoji.forEach {
        content = content.replace(
            ":${it.shortcode}:",
            "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
        )
    }
    val body = Jsoup.parse(content).body()
    body.childNodes().forEach {
        replaceMentionAndHashtag(mentions, it, host)
    }
    return body
}

private fun replaceMentionAndHashtag(
    mentions: List<Mention>,
    node: Node,
    host: String,
) {
    if (mentions.any { it.url == node.attr("href") }) {
        val mention = mentions.firstOrNull { it.url == node.attr("href") }
        val id = mention?.id
        if (id != null) {
            node.attr(
                "href",
                AppDeepLink.Profile(userKey = MicroBlogKey(id, host)),
            )
        }
    } else if (node is Element && node.normalName() == "a" && node.hasText() && node.text()
            .startsWith('#')
    ) {
        node.attr(
            "href",
            AppDeepLink.Search(node.text().trimStart('#')),
        )
    } else if (node.hasAttr("class") && node.attr("class") == "invisible") {
        node.remove()
    } else {
        node.childNodes().forEach { replaceMentionAndHashtag(mentions, it, host) }
    }
}



internal fun moe.tlaster.mfm.parser.tree.Node.toHtml(
    accountHost: String,
): Element {
    return when (this) {
        is CenterNode -> {
            Element("center").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }

        is CodeBlockNode -> {
            Element("pre").apply {
                appendChild(
                    Element("code").apply {
                        language?.let { attr("lang", it) }
                        text(code)
                    },
                )
            }
        }

        is MathBlockNode -> {
            Element("pre").apply {
                appendChild(
                    Element("code").apply {
                        attr("lang", "math")
                        text(formula)
                    },
                )
            }
        }

        is QuoteNode -> {
            Element("blockquote").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }

        is SearchNode -> {
            Element("search").apply {
                text(query)
            }
        }

        is BoldNode -> {
            Element("strong").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }

        is FnNode -> {
            Element("fn").apply {
                attr("name", name)
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }

        is ItalicNode -> {
            Element("em").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }

        is RootNode -> {
            Element("body").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }

        is SmallNode -> {
            Element("small").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }

        is StrikeNode -> {
            Element("s").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }

        is CashNode -> {
            Element("a").apply {
                attr("href", AppDeepLink.Search("$$content"))
                text("$$content")
            }
        }

        is EmojiCodeNode -> {
            Element("img").apply {
                attr("src", resolveMisskeyEmoji(emoji, accountHost))
                attr("alt", emoji)
            }
        }

        is HashtagNode -> {
            Element("a").apply {
                attr("href", AppDeepLink.Search("#$tag"))
                text("#$tag")
            }
        }

        is InlineCodeNode -> {
            Element("code").apply {
                text(code)
            }
        }

        is LinkNode -> {
            Element("a").apply {
                attr("href", url)
                text(content)
            }
        }

        is MathInlineNode -> {
            Element("code").apply {
                attr("lang", "math")
                text(formula)
            }
        }

        is MentionNode -> {
            Element("a").apply {
                val deeplink = host?.let {
                    AppDeepLink.ProfileWithNameAndHost(userName, it)
                } ?: AppDeepLink.ProfileWithNameAndHost(userName, accountHost)
                attr("href", deeplink)
                text(
                    buildString {
                        append("@")
                        append(userName)
                        if (host != null) {
                            append("@")
                            append(host)
                        }
                    },
                )
            }
        }

        is moe.tlaster.mfm.parser.tree.TextNode -> {
            Element("span").apply {
                text(content)
            }
        }

        is UrlNode -> {
            Element("a").apply {
                attr("href", url)
                text(url)
            }
        }
    }
}


private fun resolveMisskeyEmoji(name: String, accountHost: String): String {
    return name.trim(':').let {
        if (it.endsWith("@.")) {
            "https://$accountHost/emoji/${it.dropLast(2)}.webp"
        } else {
            "https://$accountHost/emoji/$it.webp"
        }
    }
}