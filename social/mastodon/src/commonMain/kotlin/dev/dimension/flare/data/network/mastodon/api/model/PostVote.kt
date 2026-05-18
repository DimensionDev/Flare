package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.Serializable

@Serializable
public data class PostVote(
    val choices: List<String>,
)
