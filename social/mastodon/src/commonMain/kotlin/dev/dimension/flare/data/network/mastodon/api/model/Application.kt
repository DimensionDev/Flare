package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
public data class Application(
    val name: String? = null,
    val website: String? = null,
)
