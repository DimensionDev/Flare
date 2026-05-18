package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
public data class Mention(
    val id: String? = null,
    val username: String? = null,
    val url: String? = null,
    val acct: String? = null,
)
