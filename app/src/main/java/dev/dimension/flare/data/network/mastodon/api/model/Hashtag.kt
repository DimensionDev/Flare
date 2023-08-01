package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
data class Hashtag(
    val name: String? = null,
    val url: String? = null,
    val history: List<History>? = null
)
