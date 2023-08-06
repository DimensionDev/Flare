package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser

internal fun DbPagingTimelineWithStatus.toUi(): UiStatus {
    return when (val status = status.status.data.content) {
        is StatusContent.Mastodon -> status.data.toUi(
            accountKey = timeline.accountKey
        )

        is StatusContent.MastodonNotification -> status.data.toUi(
            accountKey = timeline.accountKey
        )

        is StatusContent.Misskey -> status.data.toUi(
            accountKey = timeline.accountKey,
            emojis = status.emojis
        )

        is StatusContent.MisskeyNotification -> status.data.toUi(
            accountKey = timeline.accountKey,
            emojis = status.emojis
        )
    }
}

internal fun DbUser.toUi(): UiUser {
    return when (val user = content) {
        is UserContent.Mastodon -> user.data.toUi(host = userKey.host)

        is UserContent.Misskey -> user.data.toUi(
            accountHost = userKey.host,
            emojis = user.emojis
        )

        is UserContent.MisskeyLite -> user.data.toUi(
            accountHost = userKey.host,
            emojis = user.emojis
        )
    }
}
