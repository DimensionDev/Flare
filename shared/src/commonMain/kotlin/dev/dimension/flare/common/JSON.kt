package dev.dimension.flare.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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

@HiddenFromObjC
public val JSON: Json get() = json

@HiddenFromObjC
public val JSON_WITH_ENCODE_DEFAULT: Json get() = jsonWithEncodeDefault

@HiddenFromObjC
public inline fun <reified T> T.encodeJson(): String = JSON.encodeToString(this)

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun <T> T.encodeJson(serializer: KSerializer<T>): String = JSON.encodeToString(serializer, this)

@HiddenFromObjC
public inline fun <reified T> String.decodeJson(): T = JSON.decodeFromString(this)

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun <T> String.decodeJson(serializer: KSerializer<T>): T = JSON.decodeFromString(serializer, this)

@HiddenFromObjC
public val JsonElement.jsonObjectOrNull: JsonObject?
    get() = if (this is JsonObject) this else null

internal fun <T> Json.decodeFromStringWithNullableFallback(
    serializer: KSerializer<T>,
    string: String,
): T =
    try {
        decodeFromString(serializer, string)
    } catch (cause: Exception) {
        val element = parseToJsonElement(string)
        decodeFromJsonElementWithNullableFallback(serializer, element, cause)
    }

internal fun <T> Json.decodeFromJsonElementWithNullableFallback(
    serializer: KSerializer<T>,
    element: JsonElement,
    originalCause: Exception? = null,
): T {
    val sanitized = sanitizeNullableMismatches(serializer.descriptor, element)
    return try {
        decodeFromJsonElement(serializer, sanitized)
    } catch (cause: Exception) {
        throw originalCause ?: cause
    }
}

private fun sanitizeNullableMismatches(
    descriptor: SerialDescriptor,
    element: JsonElement,
): JsonElement {
    if (element is JsonNull) {
        return element
    }
    if (descriptor.isNullable && !descriptor.isCompatibleWith(element)) {
        return JsonNull
    }
    val sanitized =
        when {
            element is JsonObject && descriptor.kind == StructureKind.CLASS -> {
                sanitizeObject(descriptor, element)
            }

            element is JsonObject && descriptor.kind == StructureKind.OBJECT -> {
                sanitizeObject(descriptor, element)
            }

            element is JsonArray && descriptor.kind == StructureKind.LIST -> {
                JsonArray(element.map { sanitizeNullableMismatches(descriptor.getElementDescriptor(0), it) })
            }

            element is JsonObject && descriptor.kind == StructureKind.MAP -> {
                sanitizeMap(descriptor, element)
            }

            else -> {
                element
            }
        }
    return if (descriptor.isNullable && !descriptor.isDeeplyCompatibleWith(sanitized)) {
        JsonNull
    } else {
        sanitized
    }
}

private fun sanitizeObject(
    descriptor: SerialDescriptor,
    element: JsonObject,
): JsonObject {
    val values = element.toMutableMap()
    repeat(descriptor.elementsCount) { index ->
        val name = descriptor.getElementName(index)
        val value = values[name] ?: return@repeat
        values[name] = sanitizeNullableMismatches(descriptor.getElementDescriptor(index), value)
    }
    return JsonObject(values)
}

private fun sanitizeMap(
    descriptor: SerialDescriptor,
    element: JsonObject,
): JsonObject {
    val valueDescriptor = descriptor.getElementDescriptor(1)
    return JsonObject(
        element.mapValues { (_, value) ->
            sanitizeNullableMismatches(valueDescriptor, value)
        },
    )
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.isCompatibleWith(element: JsonElement): Boolean =
    when (kind) {
        StructureKind.CLASS,
        StructureKind.OBJECT,
        -> {
            element is JsonObject
        }

        StructureKind.LIST -> {
            element is JsonArray
        }

        StructureKind.MAP -> {
            element is JsonObject || element is JsonArray
        }

        SerialKind.ENUM -> {
            element is JsonPrimitive
        }

        SerialKind.CONTEXTUAL -> {
            true
        }

        is PolymorphicKind -> {
            element is JsonObject || element is JsonArray
        }

        else -> {
            isPrimitiveCompatibleWith(element)
        }
    }

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.isDeeplyCompatibleWith(element: JsonElement): Boolean {
    if (element is JsonNull) {
        return isNullable
    }
    if (!isCompatibleWith(element)) {
        return false
    }
    return when (kind) {
        StructureKind.CLASS,
        StructureKind.OBJECT,
        -> {
            val values = element as JsonObject
            repeat(elementsCount) { index ->
                val value = values[getElementName(index)]
                if (value == null) {
                    if (!isElementOptional(index)) {
                        return false
                    }
                } else if (!getElementDescriptor(index).isDeeplyCompatibleWith(value)) {
                    return false
                }
            }
            true
        }

        StructureKind.LIST -> {
            val valueDescriptor = getElementDescriptor(0)
            (element as JsonArray).all(valueDescriptor::isDeeplyCompatibleWith)
        }

        StructureKind.MAP -> {
            val valueDescriptor = getElementDescriptor(1)
            when (element) {
                is JsonObject -> {
                    element.values.all(valueDescriptor::isDeeplyCompatibleWith)
                }

                is JsonArray -> {
                    element.size % 2 == 0 &&
                        element.withIndex().all { (index, value) ->
                            getElementDescriptor(index % 2).isDeeplyCompatibleWith(value)
                        }
                }

                else -> {
                    false
                }
            }
        }

        SerialKind.ENUM -> {
            val value = (element as JsonPrimitive).content
            (0 until elementsCount).any { getElementName(it) == value }
        }

        else -> {
            true
        }
    }
}

private fun SerialDescriptor.isPrimitiveCompatibleWith(element: JsonElement): Boolean {
    val primitive = element as? JsonPrimitive ?: return false
    return when (kind) {
        PrimitiveKind.BOOLEAN -> {
            primitive.booleanOrNull != null
        }

        PrimitiveKind.BYTE -> {
            primitive.intOrNull?.let { it >= Byte.MIN_VALUE.toInt() && it <= Byte.MAX_VALUE.toInt() } == true
        }

        PrimitiveKind.SHORT -> {
            primitive.intOrNull?.let { it >= Short.MIN_VALUE.toInt() && it <= Short.MAX_VALUE.toInt() } == true
        }

        PrimitiveKind.INT -> {
            primitive.intOrNull != null
        }

        PrimitiveKind.LONG -> {
            primitive.longOrNull != null
        }

        PrimitiveKind.FLOAT -> {
            primitive.doubleOrNull != null
        }

        PrimitiveKind.DOUBLE -> {
            primitive.doubleOrNull != null
        }

        PrimitiveKind.CHAR -> {
            primitive.isString && primitive.content.length == 1
        }

        PrimitiveKind.STRING -> {
            primitive.isString
        }

        else -> {
            true
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@HiddenFromObjC
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
