package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.common.decodeJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MastodonException(
    @SerialName("error")
    val error: String? = null,
) : Throwable(error)

internal fun String.toMastodonExceptionOrNull(): MastodonException? =
    runCatching {
        decodeJson<MastodonException>()
    }.getOrNull()
        ?.takeIf { !it.error.isNullOrBlank() }
