package dev.dimension.flare.feature.plugin.manifest

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.wire.ComposeConfigV1
import dev.dimension.flare.feature.plugin.wire.HostIconV1
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.TimelineDisplayV1
import dev.dimension.flare.feature.plugin.wire.VisibilityV1
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class PluginManifestSchemaTest {
    private val sdk =
        sequenceOf(Path.of("sdk"), Path.of("feature/plugin/sdk"))
            .first { Files.isRegularFile(it.resolve("skill/flare-plugin/manifest.schema.json")) }
    private val schema = read("skill/flare-plugin/manifest.schema.json")
    private val definitions = schema.getValue("definitions").jsonObject

    @Test
    fun everyManifestFieldAndRequiredPropertyMatchesSerialization() {
        checkObject(schema, PluginManifestV1.serializer().descriptor)
        val properties = schema.getValue("properties").jsonObject
        assertEquals(
            PluginAbiV1.API_VERSION,
            properties
                .getValue("apiVersion")
                .jsonObject
                .getValue("const")
                .jsonPrimitive.int,
        )
        assertEquals(
            PluginAbiV1.MANIFEST_SCHEMA_VERSION,
            properties
                .getValue("schemaVersion")
                .jsonObject
                .getValue("const")
                .jsonPrimitive.int,
        )
        listOf(
            "permissions" to PluginPermissionsV1.serializer().descriptor,
            "platform" to PluginPlatformManifestV1.serializer().descriptor,
            "localizedText" to PluginTextV1.Localized.serializer().descriptor,
            "detector" to DetectorManifestV1.serializer().descriptor,
            "guest" to GuestManifestV1.serializer().descriptor,
            "capability" to CapabilityManifestV1.serializer().descriptor,
            "operation" to CapabilityOperationManifestV1.serializer().descriptor,
            "timeline" to TimelineManifestV1.serializer().descriptor,
            "profileTab" to ProfileTabManifestV1.serializer().descriptor,
            "loginMethod" to LoginMethodManifestV1.serializer().descriptor,
            "loginField" to LoginFieldManifestV1.serializer().descriptor,
            "webCookie" to WebCookieManifestV1.serializer().descriptor,
            "cookieProbe" to CookieProbeManifestV1.serializer().descriptor,
            "cookieRequirement" to CookieRequirementManifestV1.serializer().descriptor,
            "deepLink" to DeepLinkManifestV1.serializer().descriptor,
            "deepLinkTarget" to DeepLinkTargetV1.serializer().descriptor,
            "compose" to ComposeConfigV1.serializer().descriptor,
            "composeText" to ComposeConfigV1.TextConfigV1.serializer().descriptor,
            "composeMedia" to ComposeConfigV1.MediaConfigV1.serializer().descriptor,
            "composeVisibility" to ComposeConfigV1.VisibilityConfigV1.serializer().descriptor,
            "composePoll" to ComposeConfigV1.PollConfigV1.serializer().descriptor,
            "composeLanguage" to ComposeConfigV1.LanguageConfigV1.serializer().descriptor,
        ).forEach { (name, descriptor) -> checkObject(definitions.getValue(name).jsonObject, descriptor) }
        checkObject(definitions.getValue("literalSegment").jsonObject, DeepLinkPathSegmentV1.Literal.serializer().descriptor, setOf("type"))
        checkObject(definitions.getValue("captureSegment").jsonObject, DeepLinkPathSegmentV1.Capture.serializer().descriptor, setOf("type"))
    }

    @Test
    fun everyEnumAndCapabilityOperationMatchesTheHost() {
        listOf(
            "loginInteraction" to LoginInteractionV1.serializer().descriptor,
            "loginFieldType" to LoginFieldTypeV1.serializer().descriptor,
            "pageDirection" to PageDirectionV1.serializer().descriptor,
            "notificationFilter" to NotificationFilterKindV1.serializer().descriptor,
            "relationAction" to RelationActionKindV1.serializer().descriptor,
            "hostIcon" to HostIconV1.serializer().descriptor,
            "timelineDisplay" to TimelineDisplayV1.serializer().descriptor,
            "deepLinkTargetType" to DeepLinkTargetTypeV1.serializer().descriptor,
            "visibility" to VisibilityV1.serializer().descriptor,
        ).forEach { (name, descriptor) ->
            assertEquals(
                (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet(),
                definitions.getValue(name).jsonObject.strings("enum"),
                name,
            )
        }
        val capabilities =
            definitions
                .getValue("capabilities")
                .jsonObject
                .getValue("properties")
                .jsonObject
        assertEquals(PluginAbiV1.knownCapabilityOperations.keys, capabilities.keys)
        PluginAbiV1.knownCapabilityOperations.forEach { (id, operations) ->
            val name =
                capabilities
                    .getValue(id)
                    .jsonObject
                    .getValue("\$ref")
                    .jsonPrimitive.content
                    .substringAfterLast('/')
            val declared =
                definitions
                    .getValue(name)
                    .jsonObject
                    .getValue("allOf")
                    .jsonArray[1]
                    .jsonObject
                    .getValue("properties")
                    .jsonObject
                    .getValue("operations")
                    .jsonObject
            assertEquals(operations, declared.getValue("properties").jsonObject.keys, id)
            assertEquals(PluginAbiV1.requiredCapabilityOperations.getValue(id), declared.strings("required"), id)
        }
    }

    @Test
    fun sharedSchemaCasesExerciseTheRealHostValidator() {
        val fixtures = read("scripts/fixtures/manifest-schema.json")
        fixtures.getValue("cases").jsonArray.forEach { element ->
            val fixture = element.jsonObject
            val manifest = merge(fixtures.getValue("base").jsonObject, fixture.getValue("patch").jsonObject)
            val result =
                runCatching {
                    PluginJsonV1.decodeFromJsonElement<PluginManifestV1>(manifest).validate().requireValid()
                }
            assertEquals(
                fixture.getValue("hostValid").jsonPrimitive.boolean,
                result.isSuccess,
                "${fixture.getValue("name")}: ${result.exceptionOrNull()?.message}",
            )
        }
    }

    private fun read(path: String): JsonObject = PluginJsonV1.parseToJsonElement(sdk.resolve(path).readText()).jsonObject

    private fun checkObject(
        document: JsonObject,
        descriptor: SerialDescriptor,
        discriminator: Set<String> = emptySet(),
    ) {
        val fields = (0 until descriptor.elementsCount).map(descriptor::getElementName)
        assertEquals(fields.toSet() + discriminator, document.getValue("properties").jsonObject.keys, descriptor.serialName)
        assertEquals(
            fields.filterIndexed { index, _ -> !descriptor.isElementOptional(index) }.toSet() + discriminator,
            document.strings("required"),
            descriptor.serialName,
        )
        assertEquals(false, document.getValue("additionalProperties").jsonPrimitive.boolean, descriptor.serialName)
        fields.forEachIndexed { index, name ->
            val field = descriptor.getElementDescriptor(index)
            val expected =
                if (field.serialName.removeSuffix("?") == "PluginTextV1") {
                    setOf("string", "object")
                } else {
                    setOf(
                        when (field.kind) {
                            PrimitiveKind.STRING, PrimitiveKind.CHAR, SerialKind.ENUM -> "string"
                            PrimitiveKind.BOOLEAN -> "boolean"
                            PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> "integer"
                            StructureKind.LIST -> "array"
                            else -> "object"
                        },
                    )
                }
            val nullable = if (field.isNullable) setOf("null") else emptySet()
            assertEquals(
                expected + nullable,
                schemaTypes(
                    document
                        .getValue("properties")
                        .jsonObject
                        .getValue(name)
                        .jsonObject,
                ),
                "$name type",
            )
        }
    }

    private fun schemaTypes(document: JsonObject): Set<String> {
        document["\$ref"]?.let {
            return schemaTypes(definitions.getValue(it.jsonPrimitive.content.substringAfterLast('/')).jsonObject)
        }
        document["type"]?.let {
            return if (it is JsonArray) {
                it
                    .map { value ->
                        value.jsonPrimitive.content
                    }.toSet()
            } else {
                setOf(it.jsonPrimitive.content)
            }
        }
        val alternatives = document["anyOf"] ?: document["oneOf"] ?: document.getValue("allOf")
        return alternatives.jsonArray.flatMap { schemaTypes(it.jsonObject) }.toSet()
    }

    private fun JsonObject.strings(key: String): Set<String> =
        get(key)
            ?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?.toSet()
            .orEmpty()

    private fun merge(
        base: JsonObject,
        patch: JsonObject,
    ): JsonObject =
        JsonObject(
            base.toMutableMap().apply {
                patch.forEach { (key, value) ->
                    this[key] = if (value is JsonObject) merge(base[key] as? JsonObject ?: JsonObject(emptyMap()), value) else value
                }
            },
        )
}
