package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class InstanceInfoV1(
    val uri: String? = null,
    val title: String? = null,
    @SerialName("short_description")
    val shortDescription: String? = null,
    val description: String? = null,
    val version: String? = null,
)
