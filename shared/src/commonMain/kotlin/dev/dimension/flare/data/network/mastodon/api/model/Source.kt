package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class Source(
    val privacy: String? = null,
    val sensitive: Boolean? = null,
    val language: String? = null,
    val note: String? = null,
    val fields: JsonArray? = null,
    @SerialName("follow_requests_count")
    val followRequestsCount: Long? = null,
)
