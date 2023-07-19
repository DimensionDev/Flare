package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
data class Meta(
  val length: String? = null,
  val duration: Double? = null,
  val fps: Long? = null,
  val size: String? = null,
  val width: Long? = null,
  val height: Long? = null,
  val aspect: Double? = null,
  val original: Original? = null,
  val small: Small? = null
)
