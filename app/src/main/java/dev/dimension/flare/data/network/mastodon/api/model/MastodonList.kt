package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MastodonList(
    @SerialName("id")
    val id: String? = null,
    @SerialName("replies_policy")
    val repliesPolicy: String? = null,
    @SerialName("title")
    val title: String? = null
)
