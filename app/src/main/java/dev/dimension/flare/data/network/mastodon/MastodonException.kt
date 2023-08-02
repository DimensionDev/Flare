package dev.dimension.flare.data.network.mastodon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MastodonException(
    @SerialName("error")
    val error: String? = null
) : Throwable(error)
