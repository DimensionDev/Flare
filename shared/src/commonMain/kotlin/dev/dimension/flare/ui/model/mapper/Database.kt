package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.cache.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser

internal fun DbPagingTimelineWithStatusView.toUi(): UiStatus = status_content.toUi(timeline_account_key)

internal fun StatusContent.toUi(accountKey: MicroBlogKey) =
    when (this) {
        is StatusContent.Mastodon ->
            data.toUi(
                accountKey = accountKey,
            )

        is StatusContent.MastodonNotification ->
            data.toUi(
                accountKey = accountKey,
            )

        is StatusContent.Misskey ->
            data.toUi(
                accountKey = accountKey,
            )

        is StatusContent.MisskeyNotification ->
            data.toUi(
                accountKey = accountKey,
            )

        is StatusContent.Bluesky ->
            reason?.toUi(
                accountKey = accountKey,
                data = data,
            ) ?: data.toUi(
                accountKey = accountKey,
            )

        is StatusContent.BlueskyNotification ->
            data.toUi(
                accountKey = accountKey,
            )

        is StatusContent.XQT ->
            data.toUi(
                accountKey = accountKey,
            )

        is StatusContent.VVO ->
            data.toUi(
                accountKey = accountKey,
            )

        is StatusContent.VVOComment ->
            data.toUi(
                accountKey = accountKey,
            )
    }

internal fun DbUser.toUi(accountKey: MicroBlogKey): UiUser =
    when (val user = content) {
        is UserContent.Mastodon -> user.data.toUi(host = accountKey.host)

        is UserContent.Misskey ->
            user.data.toUi(
                accountKey = accountKey,
            )

        is UserContent.MisskeyLite ->
            user.data.toUi(
                accountKey = accountKey,
            )

        is UserContent.Bluesky ->
            user.data.toUi(
                accountKey = accountKey,
            )

        is UserContent.BlueskyLite ->
            user.data.toUi(
                accountKey = accountKey,
            )

        is UserContent.XQT ->
            user.data.toUi(
                accountKey = accountKey,
            )

        is UserContent.VVO ->
            user.data.toUi(
                accountKey = user_key,
            )
    }
