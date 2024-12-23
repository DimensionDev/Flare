package dev.dimension.flare.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

internal val JSON get() = json

internal inline fun <reified T> T.encodeJson(): String = JSON.encodeToString(this)

public fun <T> T.encodeJson(serializer: KSerializer<T>): String = JSON.encodeToString(serializer, this)

internal inline fun <reified T> String.decodeJson(): T = JSON.decodeFromString(this)

public fun <T> String.decodeJson(serializer: KSerializer<T>): T = JSON.decodeFromString(serializer, this)

internal val JsonElement.jsonObjectOrNull: JsonObject?
    get() = if (this is JsonObject) this else null
