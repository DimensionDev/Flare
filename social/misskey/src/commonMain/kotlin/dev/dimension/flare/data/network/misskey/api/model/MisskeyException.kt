package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.Serializable

@Serializable
internal data class MisskeyException(
    val error: Error? = null,
) : Exception(error?.message ?: "Unknown error") {
    @Serializable
    internal data class Error(
        val message: String? = null,
        val code: String? = null,
        val id: String? = null,
        val kind: String? = null,
    )
}
