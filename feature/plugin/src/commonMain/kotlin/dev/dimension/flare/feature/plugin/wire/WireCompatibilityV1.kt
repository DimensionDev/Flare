package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

internal val pluginWireSerializersV1 =
    SerializersModule {
        polymorphic(TimelineItemV1::class) {
            defaultDeserializer { type -> type?.let { UnknownVariantV1(TimelineItemV1.Unknown) } }
        }
        polymorphic(NavigationTargetV1::class) {
            defaultDeserializer { type -> type?.let { UnknownVariantV1(NavigationTargetV1.Unknown) } }
        }
    }

private class UnknownVariantV1<T>(
    private val value: T,
) : DeserializationStrategy<T> {
    override val descriptor = buildClassSerialDescriptor("flare.UnknownVariantV1")

    override fun deserialize(decoder: Decoder): T {
        (decoder as JsonDecoder)
            .decodeJsonElement()
            .wireObject()
            .getValue("type")
            .wireString()
        return value
    }
}

internal object PostDetailTypeV1Serializer : KSerializer<PostDetailTypeV1> {
    override val descriptor = PrimitiveSerialDescriptor("flare.PostDetailTypeV1", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PostDetailTypeV1 =
        when ((decoder as JsonDecoder).decodeJsonElement().wireString()) {
            "post" -> PostDetailTypeV1.Post
            "article" -> PostDetailTypeV1.Article
            "gallery" -> PostDetailTypeV1.Gallery
            "link" -> PostDetailTypeV1.Link
            else -> PostDetailTypeV1.Unknown
        }

    override fun serialize(
        encoder: Encoder,
        value: PostDetailTypeV1,
    ) {
        encoder.encodeString(
            when (value) {
                PostDetailTypeV1.Post -> "post"
                PostDetailTypeV1.Article -> "article"
                PostDetailTypeV1.Gallery -> "gallery"
                PostDetailTypeV1.Link -> "link"
                PostDetailTypeV1.Unknown -> "unknown"
            },
        )
    }
}

internal object CompatibleMediaV1Serializer : KSerializer<MediaV1> {
    override val descriptor = MediaV1.serializer().descriptor
    private val knownTypes =
        MediaTypeV1
            .serializer()
            .descriptor.elementNames
            .toSet() - "unknown"

    override fun deserialize(decoder: Decoder): MediaV1 {
        decoder as JsonDecoder
        val element = decoder.decodeJsonElement()
        val type = element.wireObject()["type"]?.wireString() ?: throw SerializationException("Missing media type")
        return if (type in knownTypes) {
            decoder.json.decodeFromJsonElement(MediaV1.serializer(), element)
        } else {
            MediaV1(id = "", type = MediaTypeV1.Unknown, url = "")
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: MediaV1,
    ) {
        (encoder as JsonEncoder).encodeSerializableValue(MediaV1.serializer(), value)
    }
}

internal object CompatibleMediaListV1Serializer : KSerializer<List<MediaV1>> by ListSerializer(CompatibleMediaV1Serializer)

internal object KnownActionsV1Serializer : JsonTransformingSerializer<List<ActionDescriptorV1>>(
    ListSerializer(ActionDescriptorV1.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val actions = element.wireArray()
        require(actions.size <= 100) { "Too many actions" }
        return JsonArray(
            actions.filter { action ->
                val name = action.wireObject()["action"]?.wireString() ?: throw SerializationException("Missing action")
                name in knownActionNames
            },
        )
    }
}

internal object KnownActionTokensV1Serializer :
    JsonTransformingSerializer<Map<SemanticActionV1, String>>(MapSerializer(SemanticActionV1.serializer(), String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val tokens = element.wireObject()
        require(tokens.size <= 100) { "Too many action tokens" }
        return JsonObject(tokens.filterKeys { it in knownActionNames })
    }
}

internal object KnownCapabilitySetsV1Serializer :
    JsonTransformingSerializer<Map<String, Set<String>>>(MapSerializer(String.serializer(), SetSerializer(String.serializer()))) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val capabilities = element.wireObject()
        require(capabilities.size <= 32) { "Too many capabilities" }
        require(capabilities.keys.all(PluginAbiV1.capabilityIdPattern::matches)) { "Invalid capability ID" }
        return JsonObject(
            capabilities
                .mapNotNull { (id, value) ->
                    val known = PluginAbiV1.knownCapabilityOperations[id] ?: return@mapNotNull null
                    val operations = value.wireArray().map { it.wireString() }.toSet()
                    require(operations.size <= 32) { "Too many operations" }
                    val supported = operations.filter { it in known }
                    if (operations.isNotEmpty() && supported.isEmpty()) null else id to JsonArray(supported.map(::JsonPrimitive))
                }.toMap(),
        )
    }
}

private val knownActionNames =
    SemanticActionV1
        .serializer()
        .descriptor.elementNames
        .toSet()

internal fun JsonElement.wireObject(): JsonObject = this as? JsonObject ?: throw SerializationException("Expected JSON object")

private fun JsonElement.wireArray(): JsonArray = this as? JsonArray ?: throw SerializationException("Expected JSON array")

private fun JsonElement.wireString(): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw SerializationException("Expected JSON string")
