package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RelationshipResponse(
    val id: String? = null,
    val following: Boolean? = null,

    @SerialName("showing_reblogs")
    val showingReblogs: Boolean? = null,

    val notifying: Boolean? = null,

    @SerialName("followed_by")
    val followedBy: Boolean? = null,

    val blocking: Boolean? = null,

    @SerialName("blocked_by")
    val blockedBy: Boolean? = null,

    val muting: Boolean? = null,

    @SerialName("muting_notifications")
    val mutingNotifications: Boolean? = null,

    val requested: Boolean? = null,

    @SerialName("domain_blocking")
    val domainBlocking: Boolean? = null,

    val endorsed: Boolean? = null,
    val note: String? = null,
)
