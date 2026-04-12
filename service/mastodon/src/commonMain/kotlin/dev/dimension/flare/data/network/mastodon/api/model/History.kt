package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
internal data class History(
    val day: String? = null,
    val uses: String? = null,
    val accounts: String? = null,
)
