package dev.dimension.flare.server.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json


private val json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

internal val JSON get() = json

internal inline fun <reified T> T.encodeJson(): String = JSON.encodeToString(this)

public fun <T> T.encodeJson(serializer: KSerializer<T>): String = JSON.encodeToString(serializer, this)

internal inline fun <reified T> String.decodeJson(): T = JSON.decodeFromString(this)

public fun <T> String.decodeJson(serializer: KSerializer<T>): T = JSON.decodeFromString(serializer, this)
