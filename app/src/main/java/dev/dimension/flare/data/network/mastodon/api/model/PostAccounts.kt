package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
data class PostAccounts(
  val account_ids: List<String>
)
