package dev.dimension.flare.feature.plugin.abi

import kotlinx.serialization.json.Json

public val PluginJsonV1: Json =
    Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
        allowSpecialFloatingPointValues = false
        allowTrailingComma = false
    }
