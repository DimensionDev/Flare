package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChannelsFeaturedRequest(
    @SerialName(value = "limit") val limit: Int? = 10,
    @SerialName(value = "allowPartial") val allowPartial: Boolean? = true,
)