package dev.dimension.flare.data.network.mastodon.api.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Notification(
    val id: String? = null,
    val type: NotificationTypes? = null,
    @SerialName("created_at")
    @Serializable(with = DateSerializer::class)
    val createdAt: Instant? = null,
    val account: Account? = null,
    val status: Status? = null,
)

@Serializable
internal enum class NotificationTypes {
    @SerialName("follow")
    Follow,

    @SerialName("favourite")
    Favourite,

    @SerialName("reblog")
    Reblog,

    @SerialName("mention")
    Mention,

    @SerialName("poll")
    Poll,

    @SerialName("follow_request")
    FollowRequest,

    @SerialName("status")
    Status,

    @SerialName("update")
    Update,
}
