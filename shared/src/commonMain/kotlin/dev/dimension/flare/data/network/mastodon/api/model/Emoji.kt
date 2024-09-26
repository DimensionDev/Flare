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

@Serializable
data class Marker(
    val notifications: Notifications? = null,
)

@Serializable
data class Notifications(
    @SerialName("last_read_id")
    val lastReadID: String? = null,
    val version: Long? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

@Serializable
data class MarkerUpdate(
    val home: UpdateContent? = null,
    val notifications: UpdateContent? = null,
)

@Serializable
data class UpdateContent(
    @SerialName("last_read_id")
    val lastReadID: String? = null,
)
