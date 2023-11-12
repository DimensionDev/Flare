package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestTokenResponse(
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("token_type")
    val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("created_at")
    val createdAt: Long? = null,
)
