package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostPoll(
    val options: List<String>? = null,
    @SerialName("expires_in")
    val expiresIn: Long? = null,
    val multiple: Boolean? = null,
    @SerialName("hide_totals")
    val hideTotals: Boolean? = null,
)
