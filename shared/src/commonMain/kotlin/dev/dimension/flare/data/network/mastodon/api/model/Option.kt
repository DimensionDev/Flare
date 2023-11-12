package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Option(
    val title: String? = null,
    @SerialName("votes_count")
    val votesCount: Long? = null,
)
