package dev.dimension.flare.data.network.ai

import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.datastore.model.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class AiCompletionServiceTest {
    @Test
    fun translationAndSummaryKeepUsingBasicSettingsWhenAgentIsIndependent() =
        runTest {
            var requests = 0
            val config =
                AppSettings.AiConfig(
                    type = AppSettings.AiConfig.Type.OpenAI("https://basic.example/v1/", "basic-key", "light", "low", "{\"seed\":1}"),
                    agentType =
                        AppSettings.AiConfig.Type.OpenAI(
                            "https://agent.example/v1/",
                            "agent-key",
                            "strong",
                            "high",
                            "{\"seed\":2}",
                        ),
                )
            val engine =
                MockEngine { request ->
                    requests += 1
                    assertEquals("https://basic.example/v1/chat/completions", request.url.toString())
                    assertEquals("Bearer basic-key", request.headers[HttpHeaders.Authorization])
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("light", body["model"]?.jsonPrimitive?.content)
                    assertEquals("low", body["reasoning_effort"]?.jsonPrimitive?.content)
                    assertEquals("1", body["seed"]?.jsonPrimitive?.content)
                    respond("""{"choices":[{"message":{"content":"remote result"}}]}""")
                }
            val onDevice =
                object : OnDeviceAI {
                    override suspend fun isAvailable(): Boolean = true

                    override suspend fun translate(
                        source: String,
                        targetLanguage: String,
                        prompt: String,
                    ): String = "local translation"

                    override suspend fun tldr(
                        source: String,
                        targetLanguage: String,
                        prompt: String,
                    ): String = "local summary"
                }
            try {
                val service = AiCompletionService(OpenAIService { configure -> HttpClient(engine) { configure() } }, onDevice)
                assertEquals("remote result", service.translate(config, "source", "zh", "translate"))
                assertEquals("remote result", service.tldr(config, "source", "zh", "summarize"))
                val local = config.copy(type = AppSettings.AiConfig.Type.OnDevice)
                assertEquals("local translation", service.translate(local, "source", "zh", "translate"))
                assertEquals("local summary", service.tldr(local, "source", "zh", "summarize"))
                assertEquals(2, requests)
            } finally {
                engine.close()
            }
        }
}
