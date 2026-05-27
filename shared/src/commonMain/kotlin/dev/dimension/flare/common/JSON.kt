package dev.dimension.flare.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

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

public val JSON: Json get() = json
public val JSON_WITH_ENCODE_DEFAULT: Json get() = jsonWithEncodeDefault

public inline fun <reified T> T.encodeJson(): String = JSON.encodeToString(this)

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun <T> T.encodeJson(serializer: KSerializer<T>): String = JSON.encodeToString(serializer, this)

public inline fun <reified T> String.decodeJson(): T = JSON.decodeFromString(this)

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun <T> String.decodeJson(serializer: KSerializer<T>): T = JSON.decodeFromString(serializer, this)

public val JsonElement.jsonObjectOrNull: JsonObject?
    get() = if (this is JsonObject) this else null

@OptIn(ExperimentalSerializationApi::class)
public class SafePolymorphicSerializer<T : Any>(
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
