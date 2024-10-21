package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.data.database.cache.model.DbMessageItemWithUser
import dev.dimension.flare.data.database.cache.model.DbMessageRoomWithLastMessageAndUser
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant

internal fun DbPagingTimelineView.render(event: StatusEvent): UiTimeline =
    status_content.render(
        timeline.accountKey,
        event,
        references =
            listOfNotNull(
                retweet_status_content?.let { ReferenceType.Retweet to it },
                quote_status_content?.let { ReferenceType.Quote to it },
                reply_status_content?.let { ReferenceType.Reply to it },
                notification_status_content?.let { ReferenceType.Notification to it },
            ).toMap(),
    )

internal fun StatusContent.render(
    accountKey: MicroBlogKey,
    event: StatusEvent,
    references: Map<ReferenceType, StatusContent> = emptyMap(),
) = when (this) {
    is StatusContent.Mastodon ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Mastodon,
            references = references,
            host = accountKey.host,
        )

    is StatusContent.MastodonNotification ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Mastodon,
            references = references,
        )

    is StatusContent.Misskey ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Misskey,
            references = references,
        )

    is StatusContent.MisskeyNotification ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Misskey,
            references = references,
        )

    is StatusContent.BlueskyReason ->
        reason.render(
            accountKey = accountKey,
            event = event as StatusEvent.Bluesky,
            references = references,
        )

    is StatusContent.Bluesky ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Bluesky,
        )

    is StatusContent.BlueskyNotification ->
        renderBlueskyNotification(
            accountKey = accountKey,
            event = event as StatusEvent.Bluesky,
            references = references,
        )

    is StatusContent.XQT ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.XQT,
            references = references,
        )

    is StatusContent.VVO ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.VVO,
        )

    is StatusContent.VVOComment ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.VVO,
        )
}

internal fun DbUser.render(accountKey: MicroBlogKey) =
    when (content) {
        is UserContent.Bluesky -> content.data.render(accountKey = accountKey)
        is UserContent.BlueskyLite -> content.data.render(accountKey = accountKey)
        is UserContent.Mastodon -> content.data.render(accountKey = accountKey, host = accountKey.host)
        is UserContent.Misskey -> content.data.render(accountKey = accountKey)
        is UserContent.MisskeyLite -> content.data.render(accountKey = accountKey)
        is UserContent.VVO -> content.data.render(accountKey = accountKey)
        is UserContent.XQT -> content.data.render(accountKey = accountKey)
    }

internal fun DbMessageRoomWithLastMessageAndUser.render(accountKey: MicroBlogKey) =
    UiDMRoom(
        key = room.roomKey,
        lastMessage = lastMessage?.render(accountKey),
        users =
            users
                .filter { it.reference.userKey != accountKey }
                .map { it.user.render(accountKey) }
                .toImmutableList(),
    )

internal fun DbMessageItemWithUser.render(accountKey: MicroBlogKey) =
    UiDMItem(
        key = message.messageKey,
        user = user.render(accountKey),
        timestamp = Instant.fromEpochMilliseconds(message.timestamp).toUi(),
        content =
            when (val content = message.content) {
                is MessageContent.Bluesky -> content.render(accountKey = accountKey)
                is MessageContent.Local ->
                    UiDMItem.Message.Text(
                        Element("span")
                            .apply {
                                appendText("content.text")
                            }.toUi(),
                    )
            },
        isFromMe = accountKey == message.userKey,
    )
