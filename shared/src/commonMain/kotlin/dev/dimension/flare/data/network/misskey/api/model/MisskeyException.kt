package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.Serializable

@Serializable
public data class MisskeyException(
    val error: Error? = null,
) : Throwable(error?.message ?: "Unknown error") {
    @Serializable
    public data class Error(
        val message: String? = null,
        val code: String? = null,
        val id: String? = null,
        val kind: String? = null,
    )
}
