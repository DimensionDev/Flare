package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ListMembership(
    @SerialName(value = "id") val id: kotlin.String,
    @SerialName(value = "createdAt") val createdAt: kotlin.String,
    @SerialName(value = "user") val user: UserLite,
    @SerialName(value = "userId") val userId: kotlin.String,
)
