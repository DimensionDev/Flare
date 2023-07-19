package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
  @SerialName("unknown")
  unknown,

  @SerialName("image")
  image,

  @SerialName("gifv")
  gifv,

  @SerialName("video")
  video,

  @SerialName("audio")
  audio,
}
