package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyCredentialsResponse(
    val name: String? = null,
    val website: String? = null,
    @SerialName("vapid_key")
    val vapidKey: String? = null,
)
