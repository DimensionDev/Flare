package dev.dimension.flare.data.database.cache

import kotlinx.serialization.Serializable

@Serializable
sealed interface StatusContent {
//    @Serializable
//    @SerialName("mastodon")
//    data class Mastodon(val data: Status) : StatusContent
//
//    @Serializable
//    @SerialName("mastodon-notification")
//    data class MastodonNotification(val data: Notification) : StatusContent
//
//    @Serializable
//    @SerialName("misskey")
//    data class Misskey(
//        val data: dev.dimension.flare.data.network.misskey.api.model.Note,
//    ) : StatusContent
//
//    @Serializable
//    @SerialName("misskey-notification")
//    data class MisskeyNotification(
//        val data: dev.dimension.flare.data.network.misskey.api.model.Notification,
//    ) : StatusContent
//
//    @Serializable
//    @SerialName("bluesky")
//    data class Bluesky(val data: PostView) : StatusContent
//
//    @Serializable
//    @SerialName("bluesky-reason")
//    data class BlueskyReason(val data: FeedViewPostReasonUnion) : StatusContent
//
//    @Serializable
//    @SerialName("bluesky-notification")
//    data class BlueskyNotification(val data: app.bsky.notification.ListNotificationsNotification) :
//        StatusContent
}