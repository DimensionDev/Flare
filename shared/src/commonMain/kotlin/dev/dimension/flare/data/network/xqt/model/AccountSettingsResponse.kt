package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountSettingsResponse(
    @SerialName("screen_name")
    val screenName: String? = null,
)
