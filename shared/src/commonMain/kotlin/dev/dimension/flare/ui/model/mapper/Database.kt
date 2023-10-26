package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.cache.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser

internal fun DbPagingTimelineWithStatusView.toUi(): UiStatus {
    return when (val status = status_content) {
        is StatusContent.Mastodon -> status.data.toUi(
            accountKey = timeline_account_key,
        )

        is StatusContent.MastodonNotification -> status.data.toUi(
            accountKey = timeline_account_key,
        )

        is StatusContent.Misskey -> status.data.toUi(
            accountKey = timeline_account_key,
        )

        is StatusContent.MisskeyNotification -> status.data.toUi(
            accountKey = timeline_account_key,
        )
        is StatusContent.Bluesky -> status.data.toUi(
            accountKey = timeline_account_key,
        )
        is StatusContent.BlueskyNotification -> status.data.toUi(
            accountKey = timeline_account_key,
        )
        is StatusContent.BlueskyReason -> status.reason.toUi(
            accountKey = timeline_account_key,
            data = status.data,
        )
    }
}

internal fun DbUser.toUi(): UiUser {
    return when (val user = content) {
        is UserContent.Mastodon -> user.data.toUi(host = user_key.host)

        is UserContent.Misskey -> user.data.toUi(
            accountHost = user_key.host,
        )

        is UserContent.MisskeyLite -> user.data.toUi(
            accountHost = user_key.host,
        )
        is UserContent.Bluesky -> user.data.toUi(
            accountHost = user_key.host,
        )
        is UserContent.BlueskyLite -> user.data.toUi(
            accountHost = user_key.host,
        )
    }
}
