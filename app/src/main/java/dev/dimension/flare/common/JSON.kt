package dev.dimension.flare.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

val JSON get() = json

inline fun <reified T> T.encodeJson(): String = JSON.encodeToString(this)

inline fun <reified T> String.decodeJson(): T = JSON.decodeFromString(this)
