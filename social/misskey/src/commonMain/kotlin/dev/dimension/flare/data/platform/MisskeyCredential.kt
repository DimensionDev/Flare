package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@SerialName("MisskeyCredential")
internal data class MisskeyCredential(
    val host: String,
    val accessToken: String,
    val nodeType: String? = null,
)
