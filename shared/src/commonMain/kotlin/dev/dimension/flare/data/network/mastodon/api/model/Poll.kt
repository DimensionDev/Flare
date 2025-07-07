package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class Poll(
    val id: String? = null,
    @SerialName("expires_at")
    @Serializable(with = DateSerializer::class)
    val expiresAt: Instant? = null,
    val expired: Boolean? = null,
    val multiple: Boolean? = null,
    @SerialName("votes_count")
    val votesCount: Long? = null,
    @SerialName("voters_count")
    val votersCount: Long? = null,
    val voted: Boolean? = null,
    @SerialName("own_votes")
    val ownVotes: List<Int>? = null,
    val options: List<Option>? = null,
    val emojis: List<Emoji>? = null,
)
