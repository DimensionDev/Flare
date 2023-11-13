package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
data class PostList(
    val title: String? = null,
    // Enumerable oneOf followed list none. Defaults to list.
    val replies_policy: String? = null,
)
