package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@SerialName("XQTCredential")
internal data class XQTCredential(
    val chocolate: String,
)
