package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.Render

internal fun DbPagingTimelineWithStatusView.render(event: StatusEvent): Render.Item =
    status_content.render(
        timeline_account_key,
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

    is StatusContent.Bluesky ->
        reason?.render(
            accountKey = accountKey,
            data = data,
            event = event as StatusEvent.Bluesky,
        ) ?: data.render(
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
            dataSource = dataSource as XQTDataSource,
        )

    is StatusContent.VVO ->
        data.render(
            accountKey = accountKey,
            dataSource = dataSource as VVODataSource,
        )

    is StatusContent.VVOComment ->
        data.render(
            accountKey = accountKey,
            dataSource = dataSource as VVODataSource,
        )
}
