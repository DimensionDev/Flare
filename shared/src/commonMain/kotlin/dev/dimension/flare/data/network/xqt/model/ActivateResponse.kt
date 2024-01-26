package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ActivateResponse(
    @SerialName("guest_token")
    val guestToken: String?,
)
