package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.common.decodeJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class MastodonException(
    @SerialName("error")
    val error: String? = null,
) : Exception(error)

public fun String.toMastodonExceptionOrNull(): MastodonException? =
    runCatching {
        decodeJson<MastodonException>()
    }.getOrNull()
        ?.takeIf { !it.error.isNullOrBlank() }
