package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimelineWithRoom
import dev.dimension.flare.data.database.cache.model.DbMessageItemWithUser
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant

internal fun DbPagingTimelineWithStatus.render(event: StatusEvent?): UiTimeline =
    status.status.data.content.render(
        event,
        references =
            status.references.associate { it.reference.referenceType to it.status.data.content },
    )

internal fun DbStatusWithReference.render(event: StatusEvent?): UiTimeline =
    status.data.content.render(
        event,
        references =
            references.associate { it.reference.referenceType to it.status.data.content },
    )

internal fun StatusContent.render(
    event: StatusEvent?,
    references: Map<ReferenceType, StatusContent> = emptyMap(),
) = when (this) {
    is StatusContent.Mastodon ->
        data.render(
            event = event as StatusEvent.Mastodon,
            references = references,
            host = event.accountKey.host,
        )

    is StatusContent.MastodonNotification ->
        data.render(
            event = event as StatusEvent.Mastodon,
            accountKey = event.accountKey,
            references = references,
        )

    is StatusContent.Misskey ->
        data.render(
            event = event as StatusEvent.Misskey,
            accountKey = event.accountKey,
            references = references,
        )

    is StatusContent.MisskeyNotification ->
        data.render(
            event = event as StatusEvent.Misskey,
            accountKey = event.accountKey,
            references = references,
        )

    is StatusContent.BlueskyReason ->
        reason.render(
            event = event as StatusEvent.Bluesky,
            accountKey = event.accountKey,
            references = references,
        )

    is StatusContent.Bluesky ->
        data.render(
            event = event as StatusEvent.Bluesky,
            accountKey = event.accountKey,
        )

    is StatusContent.BlueskyNotification ->
        renderBlueskyNotification(
            event = event as StatusEvent.Bluesky,
            accountKey = event.accountKey,
            references = references,
        )

    is StatusContent.XQT ->
        data.render(
            event = event as StatusEvent.XQT,
            accountKey = event.accountKey,
            references = references,
        )

    is StatusContent.VVO ->
        data.render(
            event = event as StatusEvent.VVO,
            accountKey = event.accountKey,
        )

    is StatusContent.VVOComment ->
        data.render(
            event = event as StatusEvent.VVO,
            accountKey = event.accountKey,
        )

    is StatusContent.RSS.Atom -> data.render()
    is StatusContent.RSS.RDF -> data.render()
    is StatusContent.RSS.Rss20 -> data.render()
}

internal fun DbUser.render(accountKey: MicroBlogKey) =
    when (content) {
        is UserContent.Bluesky -> content.data.render(accountKey = accountKey)
        is UserContent.BlueskyLite -> content.data.render(accountKey = accountKey)
        is UserContent.Mastodon ->
            content.data.render(
                accountKey = accountKey,
                host = accountKey.host,
            )

        is UserContent.Misskey -> content.data.render(accountKey = accountKey)
        is UserContent.MisskeyLite -> content.data.render(accountKey = accountKey)
        is UserContent.VVO -> content.data.render(accountKey = accountKey)
        is UserContent.XQT -> content.data.render(accountKey = accountKey)
    }

internal fun DbDirectMessageTimelineWithRoom.render(
    accountKey: MicroBlogKey,
    credential: UiAccount.Credential,
    statusEvent: StatusEvent,
) = UiDMRoom(
    key = room.room.roomKey,
    lastMessage = room.lastMessage?.render(accountKey, credential, statusEvent),
    users =
        room.users
            .filter { it.reference.userKey != accountKey }
            .map { it.user.render(accountKey) }
            .toImmutableList(),
    unreadCount = timeline.unreadCount,
)

internal fun DbMessageItemWithUser.render(
    accountKey: MicroBlogKey,
    credential: UiAccount.Credential,
    statusEvent: StatusEvent,
) = UiDMItem(
    key = message.messageKey,
    user = user.render(accountKey),
    timestamp = Instant.fromEpochMilliseconds(message.timestamp).toUi(),
    content =
        when (val content = message.content) {
            is MessageContent.Bluesky -> content.render(accountKey = accountKey)
            is MessageContent.XQT ->
                content.render(
                    accountKey = accountKey,
                    credential = credential,
                    statusEvent = statusEvent,
                )

            is MessageContent.Local ->
                UiDMItem.Message.Text(
                    Element("span")
                        .apply {
                            appendText(content.text)
                        }.toUi(),
                )
        },
    isFromMe = accountKey == message.userKey,
    sendState =
        when (val content = message.content) {
            is MessageContent.Local ->
                when (content.state) {
                    MessageContent.Local.State.SENDING -> UiDMItem.SendState.Sending
                    MessageContent.Local.State.FAILED -> UiDMItem.SendState.Failed
                }

            else -> null
        },
    showSender = message.showSender && message.userKey != accountKey,
)

internal fun DbRssSources.render() =
    UiRssSource(
        url = url,
        title = title,
        lastUpdate = Instant.fromEpochMilliseconds(lastUpdate).toUi(),
        id = id,
    )
