package dev.dimension.flare.data.database.cache.model

import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface StatusContent {
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
    data class Bluesky internal constructor(
        val data: PostView,
    ) : StatusContent

    @Serializable
    @SerialName("bluesky-reason")
    data class BlueskyReason internal constructor(
        val reason: FeedViewPostReasonUnion,
    ) : StatusContent

    @Serializable
    sealed interface BlueskyNotification : StatusContent {
        @Serializable
        @SerialName("bluesky-notification-user-list")
        data class UserList internal constructor(
            val data: List<app.bsky.notification.ListNotificationsNotification>,
            val post: PostView?,
        ) : BlueskyNotification

        @Serializable
        @SerialName("bluesky-notification-post")
        data class Post internal constructor(
            val post: PostView,
        ) : BlueskyNotification

        @Serializable
        @SerialName("bluesky-notification-normal")
        data class Normal internal constructor(
            val data: app.bsky.notification.ListNotificationsNotification,
        ) : BlueskyNotification
    }

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

    @Serializable
    @SerialName("vvo-comment")
    data class VVOComment internal constructor(
        internal val data: dev.dimension.flare.data.network.vvo.model.Comment,
    ) : StatusContent

    @Serializable
    sealed interface RSS : StatusContent {
        @Serializable
        @SerialName("rss-atom")
        data class Atom internal constructor(
            internal val data: Feed.Atom.Entry,
        ) : RSS

        @Serializable
        @SerialName("rss-rss20")
        data class Rss20 internal constructor(
            internal val data: Feed.Rss20.Item,
        ) : RSS

        @Serializable
        @SerialName("rss-rdf")
        data class RDF internal constructor(
            internal val data: Feed.RDF.Item,
        ) : RSS
    }
}

internal suspend inline fun <reified T : StatusContent> updateStatusUseCase(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
    cacheDatabase: CacheDatabase,
    update: (content: T) -> T,
) {
    val status = cacheDatabase.statusDao().get(statusKey, accountType = AccountType.Specific(accountKey)).firstOrNull()
    if (status != null && status.content is T) {
        cacheDatabase.statusDao().update(
            statusKey = statusKey,
            accountType = AccountType.Specific(accountKey),
            content = update(status.content),
        )
    }
}

internal suspend inline fun <reified T : UserContent> updateUserUseCase(
    userKey: MicroBlogKey,
    cacheDatabase: CacheDatabase,
    update: (content: T) -> T,
) {
    val user = cacheDatabase.userDao().findByKey(userKey).firstOrNull()
    if (user != null && user.content is T) {
        cacheDatabase.userDao().update(
            userKey = userKey,
            content = update(user.content),
        )
    }
}
