package dev.dimension.flare.data.network.ai

import dev.dimension.flare.data.datastore.model.AppSettings
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OpenAIServiceTest {
    private val service = OpenAIService()

    @Test
    fun buildChatCompletionBody_flattensExtraBodyIntoRoot() {
        val body =
            service.buildChatCompletionBody(
                config =
                    openAIConfig(
                        extraBody =
                            """
                            {"thinking":{"type":"enabled"},"stream":false}
                            """.trimIndent(),
                    ),
                prompt = "Hello from Flare",
            )

        assertEquals("test-model", body["model"]?.jsonPrimitive?.content)
        assertEquals(
            "Hello from Flare",
            body["messages"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "enabled",
            body["thinking"]
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(false, body["stream"]?.jsonPrimitive?.boolean)
        assertFalse(body.containsKey("extra_body"))
    }

    @Test
    fun buildChatCompletionBody_preservesBuiltInFieldsWhenExtraBodyCollides() {
        val body =
            service.buildChatCompletionBody(
                config =
                    openAIConfig(
                        reasoningEffort = "high",
                        extraBody =
                            """
                            {"model":"other-model","messages":[{"role":"assistant","content":"ignored"}],"reasoning_effort":"low","temperature":0.2}
                            """.trimIndent(),
                    ),
                prompt = "Keep built-in fields",
            )

        assertEquals("test-model", body["model"]?.jsonPrimitive?.content)
        assertEquals(
            "Keep built-in fields",
            body["messages"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("high", body["reasoning_effort"]?.jsonPrimitive?.content)
        assertEquals("0.2", body["temperature"]?.jsonPrimitive?.content)
    }

    private fun openAIConfig(
        model: String = "test-model",
        reasoningEffort: String = "",
        extraBody: String = "",
    ) = AppSettings.AiConfig.Type.OpenAI(
        serverUrl = "https://example.com",
        apiKey = "test-key",
        model = model,
        reasoningEffort = reasoningEffort,
        extraBody = extraBody,
    )
}
