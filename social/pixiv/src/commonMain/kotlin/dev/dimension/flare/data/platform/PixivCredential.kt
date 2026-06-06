package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable

@Serializable
public data class PixivCredential(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: Long,
    val clientId: String? = null,
    val clientSecret: String? = null,
)
