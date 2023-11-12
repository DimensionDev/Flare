package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Attachment(
    val id: String? = null,
    val type: MediaType? = null,
    val url: String? = null,
    @SerialName("preview_url")
    val previewURL: String? = null,
    @SerialName("remote_url")
    val remoteURL: String? = null,
    @SerialName("text_url")
    val textURL: String? = null,
    val meta: Meta? = null,
    val description: String? = null,
    val blurhash: String? = null,
)
