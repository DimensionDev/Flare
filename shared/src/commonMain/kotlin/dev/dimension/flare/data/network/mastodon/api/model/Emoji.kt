package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Emoji(
    val shortcode: String? = null,
    val url: String? = null,
    @SerialName("static_url")
    val staticURL: String? = null,
    @SerialName("visible_in_picker")
    val visibleInPicker: Boolean? = null,
    val category: String? = null,
)
