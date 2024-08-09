package dev.dimension.flare.ui.model

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Clock
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken

internal fun List<Token>.toHtml(accountKey: MicroBlogKey): Element {
    val body = Element("p")
    forEach {
        body.appendChild(it.toHtml(accountKey))
    }
    return body
}

private fun Token.toHtml(accountKey: MicroBlogKey): Node =
    when (this) {
        is CashTagToken ->
            Element("a").apply {
//                attributes["href"] = AppDeepLink.Search(accountKey, value)
                attributes().put("href", AppDeepLink.Search(accountKey, value))
                addChildren(TextNode(value))
            }
        // not supported
        is EmojiToken -> TextNode(value)
        is HashTagToken ->
            Element("a").apply {
//                attributes["href"] = AppDeepLink.Search(accountKey, value)
                attributes().put("href", AppDeepLink.Search(accountKey, value))
                addChildren(TextNode(value))
            }

        is StringToken ->
            Element("span").apply {
                // split \n
                val strings = value.split("\n")
                strings.forEachIndexed { index, s ->
                    appendChild(TextNode(s))
                    if (index != strings.size - 1) {
                        appendChild(Element("br"))
                    }
                }
            }
        is UrlToken ->
            Element("a").apply {
//                attributes["href"] = value
                attributes().put("href", value)
                addChildren(TextNode(value.trimUrl()))
            }

        is UserNameToken ->
            Element("a").apply {
//                attributes["href"] =
//                    AppDeepLink.ProfileWithNameAndHost(accountKey, value, accountKey.host)
                attributes().put("href", AppDeepLink.ProfileWithNameAndHost(accountKey, value, accountKey.host))
                addChildren(TextNode(value))
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
                            appendChild(
                                TextNode(
                                    "Sample content for ${user.name.raw} on ${user.key.host}",
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
                accountKey = user.key,
            ),
        platformType = user.platformType,
    )
