package dev.dimension.flare.data.network.misskey.api.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

@OptIn(ExperimentalSerializationApi::class)
internal object MisskeyEmojiMapSerializer :
    JsonTransformingSerializer<Map<String, String>>(MapSerializer(String.serializer(), String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> element
            is JsonArray, JsonNull -> JsonObject(emptyMap())
            else -> JsonObject(emptyMap())
        }
}
