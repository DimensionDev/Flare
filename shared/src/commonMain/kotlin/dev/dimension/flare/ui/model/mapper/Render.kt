package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.Render

internal fun DbPagingTimelineWithStatusView.render(dataSource: MicroblogDataSource): Render.Item =
    status_content.render(
        timeline_account_key,
        dataSource,
    )

internal fun StatusContent.render(
    accountKey: MicroBlogKey,
    dataSource: MicroblogDataSource,
) = when (this) {
    is StatusContent.Mastodon ->
        data.render(
            accountKey = accountKey,
            // TODO: make datasource a interface for each platform
            dataSource = dataSource as MastodonDataSource,
        )

    is StatusContent.MastodonNotification ->
        data.render(
            accountKey = accountKey,
            dataSource = dataSource as MastodonDataSource,
        )

    is StatusContent.Misskey ->
        data.render(
            accountKey = accountKey,
            dataSource = dataSource as MisskeyDataSource,
        )

    is StatusContent.MisskeyNotification ->
        data.render(
            accountKey = accountKey,
            dataSource = dataSource as MisskeyDataSource,
        )

    is StatusContent.Bluesky ->
        reason?.render(
            accountKey = accountKey,
            data = data,
            dataSource = dataSource as BlueskyDataSource,
        ) ?: data.render(
            accountKey = accountKey,
            dataSource = dataSource as BlueskyDataSource,
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
