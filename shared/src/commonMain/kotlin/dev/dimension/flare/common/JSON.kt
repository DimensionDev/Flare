package dev.dimension.flare.common

import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

private val jsonWithEncodeDefault =
    Json(json) {
        encodeDefaults = true
    }

internal val JSON get() = json
internal val JSON_WITH_ENCODE_DEFAULT get() = jsonWithEncodeDefault

internal inline fun <reified T> T.encodeJson(): String = JSON.encodeToString(this)

public fun <T> T.encodeJson(serializer: KSerializer<T>): String = JSON.encodeToString(serializer, this)

internal inline fun <reified T> String.decodeJson(): T = JSON.decodeFromString(this)

public fun <T> String.decodeJson(serializer: KSerializer<T>): T = JSON.decodeFromString(serializer, this)

internal val JsonElement.jsonObjectOrNull: JsonObject?
    get() = if (this is JsonObject) this else null

@OptIn(ExperimentalSerializationApi::class)
internal class SafePolymorphicSerializer<T : Any>(
    private val baseSerializer: KSerializer<T>,
    private val discriminator: String,
) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = baseSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: T?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            baseSerializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): T? {
        try {
            val jsonElement =
                (decoder as? JsonDecoder)?.decodeJsonElement()
                    ?: return baseSerializer.deserialize(decoder)

            if (jsonElement is JsonObject) {
                val typeDiscriminator = jsonElement[discriminator]?.jsonPrimitive?.contentOrNull
                if (typeDiscriminator != null) {
                    return try {
                        decoder.json.decodeFromJsonElement(baseSerializer, jsonElement)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            return baseSerializer.deserialize(decoder)
        } catch (e: Exception) {
            return null
        }
    }
}

internal object InstantSerializer : KSerializer<kotlin.time.Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("kotlin.time.Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): kotlin.time.Instant =
        kotlin.time.Instant.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: kotlin.time.Instant) {
        encoder.encodeString(value.toString())
    }
}