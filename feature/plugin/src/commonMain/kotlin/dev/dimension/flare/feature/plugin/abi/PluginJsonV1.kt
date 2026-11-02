package dev.dimension.flare.feature.plugin.abi

import dev.dimension.flare.feature.plugin.wire.pluginWireSerializersV1
import kotlinx.serialization.json.Json

public val PluginJsonV1: Json =
    Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        serializersModule = pluginWireSerializersV1
        isLenient = false
        allowSpecialFloatingPointValues = false
        allowTrailingComma = false
    }
