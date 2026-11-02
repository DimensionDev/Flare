package dev.dimension.flare.feature.plugin.manifest

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.wire.wireObject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

internal object KnownManifestCapabilitiesV1Serializer :
    JsonTransformingSerializer<Map<String, CapabilityManifestV1>>(MapSerializer(String.serializer(), CapabilityManifestV1.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val capabilities = element.wireObject()
        require(capabilities.size <= 32) { "Too many capabilities" }
        require(capabilities.keys.all(PluginAbiV1.capabilityIdPattern::matches)) { "Invalid capability ID" }
        return JsonObject(
            capabilities
                .mapNotNull { (id, value) ->
                    val known = PluginAbiV1.knownCapabilityOperations[id] ?: return@mapNotNull null
                    val declaration = value.wireObject()
                    val operations = declaration["operations"]?.wireObject()
                    require(operations == null || operations.size <= 32) { "Too many operations" }
                    val supported = operations?.filterKeys { it in known }
                    if (operations?.isNotEmpty() == true && supported?.isEmpty() == true) return@mapNotNull null
                    id to
                        if (supported == null) {
                            declaration
                        } else {
                            JsonObject(declaration + ("operations" to JsonObject(supported)))
                        }
                }.toMap(),
        )
    }
}
