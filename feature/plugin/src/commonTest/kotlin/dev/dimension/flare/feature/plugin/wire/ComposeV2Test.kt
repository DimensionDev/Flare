package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeV2Test {
    @Test
    fun referenceHasOneTypedTargetAndPreservesOpaqueKeys() {
        val key = EntityKeyV1("opaque/id:42", "remote.example")
        listOf(ComposeReferenceV2.Reply(key), ComposeReferenceV2.Quote(key)).forEach { reference ->
            val request = ComposeRequestV2("caption", VisibilityV1.Public, reference = reference)
            val encoded = PluginJsonV1.encodeToString(ComposeRequestV2.serializer(), request)
            val json = PluginJsonV1.parseToJsonElement(encoded).jsonObject
            assertFalse("replyTo" in json || "quoteTo" in json)
            val type = if (reference is ComposeReferenceV2.Reply) "reply" else "quote"
            assertEquals(
                type,
                json
                    .getValue("reference")
                    .jsonObject
                    .getValue("type")
                    .jsonPrimitive.content,
            )
            assertEquals(request, PluginJsonV1.decodeFromString<ComposeRequestV2>(encoded))
        }
        assertFailsWith<SerializationException> {
            PluginJsonV1.decodeFromString<ComposeRequestV2>(
                """{"text":"caption","visibility":"public","reference":{"type":"new","key":{"id":"42","host":"remote.example"}}}""",
            )
        }
        assertFailsWith<SerializationException> {
            PluginJsonV1.decodeFromString<ComposeRequestV2>("""{"text":"caption","visibility":"public","reference":{"type":"quote"}}""")
        }
    }

    @Test
    fun modeDeclarationsAreRequiredAndOldNegotiatedComposeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            PluginJsonV1.decodeFromString<ComposeConfigV2>("""{"text":{"maxLength":100}}""").requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            PluginJsonV1
                .decodeFromString<ComposeConfigV2>(
                    """{"supportedTypes":[]}""",
                ).requireValid()
        }
        val failure =
            assertFails {
                PluginJsonV1.decodeFromString<LoginSuccessV1>(
                    """{
              "accountId":"me","origin":"https://example.com","credential":{},
              "profile":{"key":{"id":"me","host":"example.com"},"handle":"me","displayName":"Me"},
              "capabilities":{"flare.datasource.compose/v1":["publish"]}
            }""",
                )
            }
        assertTrue(failure.message.orEmpty().contains("compose/v1"))
    }
}
