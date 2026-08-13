package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.ui.model.UiTextArgument
import kotlinx.serialization.Serializable

@Serializable
public data class WireTextV1(
    val value: String? = null,
    val key: String? = null,
    val fallback: String? = null,
    val args: Map<String, UiTextArgument> = emptyMap(),
)
