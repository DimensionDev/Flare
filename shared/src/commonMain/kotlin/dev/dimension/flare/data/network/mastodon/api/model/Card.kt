package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Card(
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val type: String? = null,
    @SerialName("author_name")
    val authorName: String? = null,
    @SerialName("author_url")
    val authorURL: String? = null,
    @SerialName("provider_name")
    val providerName: String? = null,
    @SerialName("provider_url")
    val providerURL: String? = null,
    val html: String? = null,
    val width: Long? = null,
    val height: Long? = null,
    val image: String? = null,
    @SerialName("embed_url")
    val embedURL: String? = null,
)
