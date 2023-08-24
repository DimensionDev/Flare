package dev.dimension.flare.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

val JSON get() = json

inline fun <reified T> T.encodeJson(): String = JSON.encodeToString(this)

inline fun <reified T> String.decodeJson(): T = JSON.decodeFromString(this)

val JsonElement.jsonObjectOrNull: JsonObject?
    get() = if (this is JsonObject) this else null
