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

val JSON get() = json

inline fun <reified T> T.encodeJson(): String = JSON.encodeToString(this)

fun <T> T.encodeJson(serializer: KSerializer<T>): String = JSON.encodeToString(serializer, this)

inline fun <reified T> String.decodeJson(): T = JSON.decodeFromString(this)

fun <T> String.decodeJson(serializer: KSerializer<T>): T = JSON.decodeFromString(serializer, this)

val JsonElement.jsonObjectOrNull: JsonObject?
    get() = if (this is JsonObject) this else null
