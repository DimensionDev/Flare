package dev.dimension.flare.ui.model

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.DeeplinkRoute.Companion.toUri
import kotlinx.collections.immutable.persistentListOf
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import kotlin.time.Clock

internal fun List<Token>.toHtml(accountKey: MicroBlogKey): Element {
    val body = Element("span")
    forEach {
        body.appendChild(it.toHtml(accountKey))
    }
    return body
}

private fun Token.toHtml(accountKey: MicroBlogKey): Node =
    when (this) {
        is CashTagToken ->
            Element("a").apply {
                attributes().put("href", DeeplinkRoute.Search(AccountType.Specific(accountKey), value).toUri())
                addChildren(TextNode(value))
            }
        // not supported
        is EmojiToken -> TextNode(value)
        is HashTagToken ->
            Element("a").apply {
                attributes().put("href", DeeplinkRoute.Search(AccountType.Specific(accountKey), value).toUri())
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
                attributes().put("href", value)
                addChildren(TextNode(value.trimUrl()))
            }

        is UserNameToken ->
            Element("a").apply {
                attributes().put(
                    "href",
                    DeeplinkRoute.Profile
                        .UserNameWithHost(
                            accountType = AccountType.Specific(accountKey),
                            userName = value.trimStart('@'),
                            host = accountKey.host,
                        ).toUri(),
                )
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

public fun createSampleStatus(user: UiUserV2): UiTimeline =
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
                                    "Sample content for ${user.name.raw} on ${user.key.host} ",
                                ),
                            )
                            appendChild(
                                Element("a")
                                    .apply {
                                        attributes().put(
                                            "href",
                                            DeeplinkRoute.Search(AccountType.Specific(user.key), "#flare").toUri(),
                                        )
                                        addChildren(TextNode("#flare"))
                                    },
                            )
                        }.toUi(),
                actions =
                    persistentListOf(
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.Reply,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                            count = UiNumber(10),
                        ),
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.Retweet,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
                            count = UiNumber(20),
                        ),
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.Like,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                            count = UiNumber(30),
                        ),
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    ),
                poll = null,
                statusKey = MicroBlogKey(id = "123", host = user.key.host),
                card = null,
                createdAt = Clock.System.now().toUi(),
                bottomContent = null,
                topEndContent = null,
                aboveTextContent = null,
                onClicked = {},
                onMediaClicked = { _, _ -> },
                platformType = user.platformType,
                url = "",
            ),
    )
