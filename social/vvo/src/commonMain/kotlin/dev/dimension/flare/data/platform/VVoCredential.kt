package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@SerialName("VVoCredential")
internal data class VVoCredential(
    val chocolate: String,
    val lastCookieRefreshEpochMillis: Long? = null,
)
