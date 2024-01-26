package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Account(
    val id: String? = null,
    val username: String? = null,
    val acct: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val locked: Boolean? = null,
    val bot: Boolean? = null,
    val discoverable: Boolean? = null,
    val group: Boolean? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val note: String? = null,
    val url: String? = null,
    val avatar: String? = null,
    @SerialName("avatar_static")
    val avatarStatic: String? = null,
    val header: String? = null,
    @SerialName("header_static")
    val headerStatic: String? = null,
    @SerialName("followers_count")
    val followersCount: Long? = null,
    @SerialName("following_count")
    val followingCount: Long? = null,
    @SerialName("statuses_count")
    val statusesCount: Long? = null,
    @SerialName("last_status_at")
    val lastStatusAt: String? = null,
    val emojis: List<Emoji>? = null,
    val fields: List<Field>? = null,
    val source: Source? = null,
)
