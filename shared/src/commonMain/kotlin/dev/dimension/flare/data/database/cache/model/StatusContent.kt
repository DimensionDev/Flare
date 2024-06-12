package dev.dimension.flare.data.database.cache.model

import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://github.com/cashapp/sqldelight/issues/1333
@Serializable
sealed interface StatusContent {
    @Serializable
    @SerialName("mastodon")
    data class Mastodon internal constructor(
        internal val data: dev.dimension.flare.data.network.mastodon.api.model.Status,
    ) : StatusContent

    @Serializable
    @SerialName("mastodon-notification")
    data class MastodonNotification internal constructor(
        internal val data: dev.dimension.flare.data.network.mastodon.api.model.Notification,
    ) : StatusContent

    @Serializable
    @SerialName("misskey")
    data class Misskey internal constructor(
        internal val data: dev.dimension.flare.data.network.misskey.api.model.Note,
    ) : StatusContent

    @Serializable
    @SerialName("misskey-notification")
    data class MisskeyNotification internal constructor(
        internal val data: dev.dimension.flare.data.network.misskey.api.model.Notification,
    ) : StatusContent

    @Serializable
    @SerialName("bluesky")
    data class Bluesky(
        val data: PostView,
        val reason: FeedViewPostReasonUnion?,
    ) : StatusContent

    @Serializable
    @SerialName("bluesky-notification")
    data class BlueskyNotification(
        val data: app.bsky.notification.ListNotificationsNotification,
    ) : StatusContent

    @Serializable
    @SerialName("XQT")
    data class XQT internal constructor(
        internal val data: dev.dimension.flare.data.network.xqt.model.Tweet,
    ) : StatusContent

    @Serializable
    @SerialName("vvo")
    data class VVO internal constructor(
        internal val data: dev.dimension.flare.data.network.vvo.model.Status,
    ) : StatusContent
}

internal inline fun <reified T : StatusContent> updateStatusUseCase(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
    cacheDatabase: CacheDatabase,
    update: (content: T) -> T,
) {
    val status = cacheDatabase.dbStatusQueries.get(statusKey, accountKey).executeAsOneOrNull()
    if (status != null && status.content is T) {
        cacheDatabase.dbStatusQueries.update(update(status.content), statusKey, accountKey)
    }
}

internal inline fun <reified T : UserContent> updateUserUseCase(
    userKey: MicroBlogKey,
    cacheDatabase: CacheDatabase,
    update: (content: T) -> T,
) {
    val user = cacheDatabase.dbUserQueries.findByKey(userKey).executeAsOneOrNull()
    if (user != null && user.content is T) {
        cacheDatabase.dbUserQueries.update(update(user.content), userKey)
    }
}
