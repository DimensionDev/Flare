package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
data class Context(
  val ancestors: List<Status>? = null,
  val descendants: List<Status>? = null,
)
