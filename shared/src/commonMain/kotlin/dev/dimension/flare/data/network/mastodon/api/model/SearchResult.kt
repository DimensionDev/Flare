package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResult(
    val accounts: List<Account>? = null,
    val statuses: List<Status>? = null,
    val hashtags: List<Hashtag>? = null,
)
