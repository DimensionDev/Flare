package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Original(
    val width: Long? = null,
    val height: Long? = null,
    @SerialName("frame_rate")
    val frameRate: String? = null,
    val duration: Double? = null,
    val bitrate: Long? = null,
    val size: String? = null,
    val aspect: Double? = null,
)
