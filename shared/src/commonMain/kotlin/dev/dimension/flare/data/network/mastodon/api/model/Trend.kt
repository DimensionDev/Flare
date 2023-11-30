package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Trend(
    @SerialName("history")
    val history: List<TrendHistory>? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("url")
    val url: String? = null,
)

@Serializable
data class TrendHistory(
    @SerialName("accounts")
    val accounts: String? = null,
    @SerialName("day")
    val day: String? = null,
    @SerialName("uses")
    val uses: String? = null,
)

@Serializable
data class Suggestions(
    val source: String? = null,
    val account: Account? = null,
)
