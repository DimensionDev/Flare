package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
data class Small(
    val width: Long? = null,
    val height: Long? = null,
    val size: String? = null,
    val aspect: Double? = null
)
