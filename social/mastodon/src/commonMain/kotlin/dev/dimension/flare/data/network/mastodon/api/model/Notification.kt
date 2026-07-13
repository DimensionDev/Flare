package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

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
internal enum class NotificationTypes(
    val apiValue: String,
) {
    @SerialName("follow")
    Follow("follow"),

    @SerialName("favourite")
    Favourite("favourite"),

    @SerialName("reblog")
    Reblog("reblog"),

    @SerialName("mention")
    Mention("mention"),

    @SerialName("poll")
    Poll("poll"),

    @SerialName("follow_request")
    FollowRequest("follow_request"),

    @SerialName("status")
    Status("status"),

    @SerialName("update")
    Update("update"),
}
