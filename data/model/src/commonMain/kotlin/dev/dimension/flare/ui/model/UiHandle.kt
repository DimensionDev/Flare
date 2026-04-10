package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public data class UiHandle(
    val raw: String,
    val host: String,
) {
    val normalizedRaw: String
        get() = raw.trim().removePrefix("@").substringBefore("@")

    val normalizedHost: String
        get() = host.trim().removePrefix("@")

    val canonical: String
        get() = "@$normalizedRaw@$normalizedHost"
}
