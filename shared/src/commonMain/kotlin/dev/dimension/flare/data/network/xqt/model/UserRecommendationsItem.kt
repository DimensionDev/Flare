package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserRecommendationsItem(
    val token: String? = null,
    val user: UserLegacy? = null,

    @SerialName("user_id")
    val userID: String? = null
)