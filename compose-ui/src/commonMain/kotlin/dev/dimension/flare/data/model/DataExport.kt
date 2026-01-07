package dev.dimension.flare.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class DataExport(
    val appDatabase: JsonElement,
    val settings: JsonElement,
)
