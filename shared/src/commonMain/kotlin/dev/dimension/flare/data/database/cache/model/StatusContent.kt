package dev.dimension.flare.data.database.cache.model

import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import com.moriatsushi.koject.inject
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface StatusContent {
    @Serializable
    @SerialName("mastodon")
    data class Mastodon(val data: dev.dimension.flare.data.network.mastodon.api.model.Status) :
        StatusContent

    @Serializable
    @SerialName("mastodon-notification")
    data class MastodonNotification(val data: dev.dimension.flare.data.network.mastodon.api.model.Notification) :
        StatusContent

    @Serializable
    @SerialName("misskey")
    data class Misskey(
        val data: dev.dimension.flare.data.network.misskey.api.model.Note,
    ) : StatusContent

    @Serializable
    @SerialName("misskey-notification")
    data class MisskeyNotification(
        val data: dev.dimension.flare.data.network.misskey.api.model.Notification,
    ) : StatusContent

    @Serializable
    @SerialName("bluesky")
    data class Bluesky(val data: PostView) : StatusContent

    @Serializable
    @SerialName("bluesky-reason")
    data class BlueskyReason(val reason: FeedViewPostReasonUnion, val data: PostView) : StatusContent

    @Serializable
    @SerialName("bluesky-notification")
    data class BlueskyNotification(val data: app.bsky.notification.ListNotificationsNotification) :
        StatusContent
}


internal inline fun <reified T : StatusContent> updateStatusUseCase(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
    update: (content: T) -> T,
    cacheDatabase: CacheDatabase = inject(),
) {
    val status = cacheDatabase.dbStatusQueries.get(statusKey, accountKey).executeAsOneOrNull()
    if (status != null && status.content is T) {
        cacheDatabase.dbStatusQueries.update(update(status.content), statusKey, accountKey)
    }
}