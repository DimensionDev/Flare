package dev.dimension.flare.data.network.vvo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProfileData(
    val user: User? = null,
    val statuses: List<Status>? = null,
    val more: String? = null,
    val fans: String? = null,
    val follow: String? = null,
    val button: Button? = null,
)

@Serializable
internal data class Button(
    val type: String? = null,
    val name: String? = null,
    @SerialName("sub_type")
    val subType: Long? = null,
    val params: Params? = null,
)

@Serializable
internal data class Params(
    val uid: String? = null,
)
