package dev.dimension.flare.data.network.misskey.api.model.response

import dev.dimension.flare.data.network.misskey.api.model.User
import kotlinx.serialization.Serializable

@Serializable
internal data class MiAuthCheckResponse(
    val ok: Boolean? = null,
    val token: String? = null,
    val user: User? = null,
)
