package dev.dimension.flare.ui.model

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf

public fun createSampleStatus(user: UiProfile): UiTimelineV2.Post =
    UiTimelineV2.Post(
        message = null,
        platformType = user.platformType,
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
                    icon = UiIcon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(10),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Retweet,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
                    count = UiNumber(20),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Like,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                    count = UiNumber(30),
                ),
                ActionMenu.Item(
                    icon = UiIcon.More,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                ),
            ),
        poll = null,
        statusKey = MicroBlogKey(id = "123", host = user.key.host),
        card = null,
        createdAt = Clock.System.now().toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        parents = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Specific(user.key),
    )
