package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateApplicationResponse(
    val id: String? = null,
    val name: String? = null,
    val website: String? = null,
    @SerialName("redirect_uri")
    val redirectURI: String,
    @SerialName("client_id")
    val clientID: String,
    @SerialName("client_secret")
    val clientSecret: String,
    @SerialName("vapid_key")
    val vapidKey: String? = null,
)
