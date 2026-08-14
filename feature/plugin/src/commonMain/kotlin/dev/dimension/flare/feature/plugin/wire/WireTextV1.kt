package dev.dimension.flare.feature.plugin.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
public data class WireTextV1(
    val value: String? = null,
    val key: String? = null,
    val fallback: String? = null,
    val args: Map<String, JsonPrimitive> = emptyMap(),
)
