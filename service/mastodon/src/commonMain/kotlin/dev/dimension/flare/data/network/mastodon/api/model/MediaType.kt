package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class MediaType {
    @SerialName("unknown")
    Unknown,

    @SerialName("image")
    Image,

    @SerialName("gifv")
    GifV,

    @SerialName("video")
    Video,

    @SerialName("audio")
    Audio,
}
