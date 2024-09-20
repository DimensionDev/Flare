package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline

internal fun DbPagingTimelineView.render(event: StatusEvent): UiTimeline =
    status_content.render(
        timeline.accountKey,
        event,
    )

internal fun StatusContent.render(
    accountKey: MicroBlogKey,
    event: StatusEvent,
) = when (this) {
    is StatusContent.Mastodon ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Mastodon,
        )

    is StatusContent.MastodonNotification ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Mastodon,
        )

    is StatusContent.Misskey ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Misskey,
        )

    is StatusContent.MisskeyNotification ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Misskey,
        )

    is StatusContent.BlueskyReason ->
        reason.render(
            accountKey = accountKey,
            data = data,
            event = event as StatusEvent.Bluesky,
        )

    is StatusContent.Bluesky ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.Bluesky,
        )

    is StatusContent.BlueskyNotification ->
        data.render(
            accountKey = accountKey,
        )

    is StatusContent.XQT ->
        data.render(
            accountKey = accountKey,
            event = event as StatusEvent.XQT,
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
        is UserContent.Mastodon -> content.data.render(accountKey = accountKey)
        is UserContent.Misskey -> content.data.render(accountKey = accountKey)
        is UserContent.MisskeyLite -> content.data.render(accountKey = accountKey)
        is UserContent.VVO -> content.data.render(accountKey = accountKey)
        is UserContent.XQT -> content.data.render(accountKey = accountKey)
    }
