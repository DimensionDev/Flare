package dev.dimension.flare.feature.agent.runtime

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.feature.agent.common.AgentPhase
import dev.dimension.flare.feature.agent.common.AgentToolSet
import dev.dimension.flare.feature.agent.common.FlareAgentRequest
import dev.dimension.flare.feature.agent.common.createAgent
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentImageFallbackTest {
    @Test
    fun downloadFailureDoesNotTreatUrlWordsAsProviderCapabilities() =
        runTest {
            val url = "https://image.example/unsupported.jpg"
            Fixture(
                listOf(400 to errorBody(".messages[1].image[0]: Failed to download image from $url"), 200 to SUCCESS),
                images = listOf(image.copy(url = url)),
            ).use { fixture ->
                assertEquals("done", fixture.execute(prompt(url)))
                assertEquals(1, fixture.downloads)
                assertEquals(listOf(INLINE), fixture.requests.last().imageUrls())
            }
        }

    @Test
    fun deepSeekDownloadFailureRetriesWithInlineImageWithoutChangingOriginalPrompt() =
        runTest {
            Fixture(listOf(400 to DOWNLOAD_ERROR, 200 to SUCCESS)).use { fixture ->
                val prompt = prompt()
                val original = Json.encodeToString(prompt)
                assertEquals("done", fixture.execute(prompt))
                assertEquals(1, fixture.downloads)
                assertEquals(listOf(URL), fixture.requests[0].imageUrls())
                assertEquals(listOf(INLINE), fixture.requests[1].imageUrls())
                assertEquals(original, Json.encodeToString(prompt))
            }
        }

    @Test
    fun successfulUrlDoesNotDownloadOrReencode() =
        runTest {
            Fixture(listOf(200 to SUCCESS)).use { fixture ->
                assertEquals("done", fixture.execute(prompt()))
                assertEquals(0, fixture.downloads)
                assertEquals(listOf(URL), fixture.requests.single().imageUrls())
            }
        }

    @Test
    fun inlineFailureUsesTextAndLaterCallsStayTextOnly() =
        runTest {
            Fixture(listOf(400 to DOWNLOAD_ERROR, 400 to DOWNLOAD_ERROR, 200 to SUCCESS, 200 to SUCCESS)).use { fixture ->
                val original = prompt(URL, URL)
                assertEquals("done", fixture.execute(original))
                assertEquals("done", fixture.execute(original))
                assertEquals(1, fixture.downloads)
                assertEquals(listOf(INLINE, INLINE), fixture.requests[1].imageUrls())
                fixture.requests.drop(2).forEach {
                    assertTrue(it.imageUrls().isEmpty())
                    assertTrue(it.toString().contains("Images could not be read"))
                }
                assertEquals(1, fixture.phases.count { it == AgentPhase.ImagesUnsupportedFallback })
            }
        }

    @Test
    fun partialDownloadFailureDiscardsAllImagesIncludingHistory() =
        runTest {
            val second = image.copy(url = "https://image.example/second.png")
            Fixture(listOf(400 to DOWNLOAD_ERROR, 200 to SUCCESS), listOf(image, second), prepare = { media, _ ->
                if (media == second) error("download failed")
                inlineImage()
            }).use { fixture ->
                val prompt =
                    Prompt.build("history") {
                        user {
                            text("Earlier post")
                            image(URL)
                        }
                        assistant("Earlier answer")
                        user {
                            text("Current post")
                            image(second.url)
                        }
                    }
                assertEquals("done", fixture.execute(prompt))
                assertEquals(2, fixture.downloads)
                assertTrue(
                    fixture.requests
                        .last()
                        .imageUrls()
                        .isEmpty(),
                )
                assertEquals(
                    3,
                    fixture.requests
                        .last()
                        .getValue("messages")
                        .jsonArray.size,
                )
            }
        }

    @Test
    fun unknownHistoricalImageNeverBorrowsCredentials() =
        runTest {
            Fixture(listOf(400 to DOWNLOAD_ERROR, 200 to SUCCESS)).use { fixture ->
                assertEquals("done", fixture.execute(prompt("https://another-account.example/image.jpg")))
                assertEquals(0, fixture.downloads)
                assertTrue(
                    fixture.requests
                        .last()
                        .imageUrls()
                        .isEmpty(),
                )
            }
        }

    @Test
    fun unsupportedImagesGoStraightToText() =
        runTest {
            Fixture(listOf(400 to errorBody("image_url: unknown variant, expected `text`"), 200 to SUCCESS)).use { fixture ->
                assertEquals("done", fixture.execute(prompt()))
                assertEquals(0, fixture.downloads)
                assertTrue(
                    fixture.requests
                        .last()
                        .imageUrls()
                        .isEmpty(),
                )
            }
        }

    @Test
    fun unrelatedErrorsAreNotRetried() =
        runTest {
            for ((status, body) in listOf(
                401 to DOWNLOAD_ERROR,
                429 to DOWNLOAD_ERROR,
                500 to DOWNLOAD_ERROR,
                400 to errorBody("Invalid reasoning_effort"),
                400 to errorBody("tool image_search is unsupported"),
                400 to errorBody("Invalid tool download_image"),
            )) {
                Fixture(listOf(status to body)).use { fixture ->
                    assertFailsWith<Exception> { fixture.execute(prompt()) }
                    assertEquals(1, fixture.requests.size)
                    assertEquals(0, fixture.downloads)
                }
            }
        }

    @Test
    fun cancellationDuringPreparationDoesNotSendTextRequest() =
        runTest {
            Fixture(listOf(400 to DOWNLOAD_ERROR), prepare = { _, _ -> throw CancellationException("cancelled") }).use { fixture ->
                assertFailsWith<CancellationException> { fixture.execute(prompt()) }
                assertEquals(1, fixture.requests.size)
                assertTrue(AgentPhase.ImagesUnsupportedFallback !in fixture.phases)
            }
        }

    @Test
    fun preparationDeadlineFallsBackInsteadOfHanging() =
        runTest {
            Fixture(listOf(400 to DOWNLOAD_ERROR, 200 to SUCCESS), prepare = { _, _ ->
                delay(61_000)
                inlineImage()
            }).use { fixture ->
                assertEquals("done", fixture.execute(prompt()))
                assertTrue(
                    fixture.requests
                        .last()
                        .imageUrls()
                        .isEmpty(),
                )
            }
        }

    @Test
    fun textFailureStopsAfterThreeRequests() =
        runTest {
            Fixture(List(3) { 400 to DOWNLOAD_ERROR }).use { fixture ->
                val error = assertFailsWith<Exception> { fixture.execute(prompt()) }
                assertEquals(1, error.suppressedExceptions.size)
                assertEquals(
                    1,
                    error.suppressedExceptions
                        .single()
                        .suppressedExceptions.size,
                )
                assertEquals(3, fixture.requests.size)
                assertTrue(
                    fixture.requests
                        .last()
                        .imageUrls()
                        .isEmpty(),
                )
            }
        }

    @Test
    fun repeatedImagesShareDownloadButStillCountTowardsRequestBudget() =
        runTest {
            val budgets = mutableListOf<Int>()
            Fixture(listOf(413 to errorBody("image size exceeds maximum"), 200 to SUCCESS, 200 to SUCCESS), prepare = { _, maxBytes ->
                budgets += maxBytes
                inlineImage()
            }).use { fixture ->
                val prompt = prompt(*Array(5) { URL })
                assertEquals("done", fixture.execute(prompt))
                assertEquals("done", fixture.execute(prompt))
                assertEquals(listOf(16 * 1024 * 1024 / 5), budgets)
                assertEquals(1, fixture.downloads)
                assertTrue(fixture.requests.drop(1).all { it.imageUrls() == List(5) { INLINE } })
            }
        }

    @Test
    fun oversizedPreparedOutputGoesToText() =
        runTest {
            Fixture(listOf(400 to DOWNLOAD_ERROR, 200 to SUCCESS), prepare = { _, maxBytes ->
                AttachmentSource.Image(AttachmentContent.Binary.Bytes(ByteArray(maxBytes + 1)), "jpg", "image/jpeg")
            }).use { fixture ->
                assertEquals("done", fixture.execute(prompt()))
                assertTrue(
                    fixture.requests
                        .last()
                        .imageUrls()
                        .isEmpty(),
                )
            }
        }

    @Test
    fun failureAfterToolExecutionRetriesOnlyModelAndHistoryKeepsUrls() =
        runTest {
            val toolResponse =
                """
                {"id":"response","object":"chat.completion","created":0,"model":"deepseek-flash","choices":[{
                  "index":0,"message":{"role":"assistant","content":null,"tool_calls":[{
                    "id":"call-1","type":"function","function":{"name":"echo","arguments":"{\"text\":\"hello\"}"}
                  }]},"finish_reason":"tool_calls"
                }]}
                """.trimIndent()
            Fixture(listOf(200 to toolResponse, 400 to DOWNLOAD_ERROR, 200 to SUCCESS)).use { fixture ->
                val tool = EchoTool()
                val history = MemoryHistory()
                val request =
                    FlareAgentRequest(
                        prompt = prompt(),
                        systemPrompt = "Use echo",
                        agentId = "image-test",
                        strategyName = "image-test",
                        analyzeNodeName = "analyze",
                        executeToolsNodeName = "tools",
                        sendToolResultsNodeName = "results",
                        images = listOf(image),
                        toolSet = AgentToolSet.Empty.copy(toolRegistry = ToolRegistry { tool(tool) }),
                    )
                val agent = fixture.runtime.createAgent(request, {}, history, fixture::prepare)
                try {
                    assertEquals("done", agent.run(request.prompt, "conversation"))
                    assertEquals(1, tool.calls)
                    assertEquals(
                        listOf("done"),
                        history.messages
                            .filterIsInstance<Message.Assistant>()
                            .map { it.textContent() }
                            .filter { it.isNotBlank() },
                    )
                    assertEquals(3, fixture.requests.size)
                    assertEquals(listOf(INLINE), fixture.requests.last().imageUrls())
                    fixture.requests.forEach {
                        assertEquals("deepseek-flash", it.getValue("model").jsonPrimitive.content)
                        assertEquals("high", it.getValue("reasoning_effort").jsonPrimitive.content)
                        assertEquals("7", it.getValue("seed").jsonPrimitive.content)
                    }
                    assertEquals(fixture.requests[1]["tools"], fixture.requests[2]["tools"])
                    assertTrue(
                        fixture.requests.last().getValue("messages").jsonArray.any {
                            it.jsonObject["tool_call_id"]?.jsonPrimitive?.content ==
                                "call-1"
                        },
                    )
                    val stored = Json.encodeToString(history.messages)
                    assertTrue(stored.contains(URL))
                    assertTrue(!stored.contains("AQID"))
                } finally {
                    agent.close()
                }
            }
        }

    private class Fixture(
        responses: List<Pair<Int, String>>,
        images: List<UiMedia.Image> = listOf(image),
        private val prepare: suspend (UiMedia.Image, Int) -> AttachmentSource.Image = { _, _ -> inlineImage() },
    ) : AutoCloseable {
        val requests = mutableListOf<JsonObject>()
        val phases = mutableListOf<AgentPhase>()
        var downloads = 0
        private val client =
            HttpClient(
                MockEngine { request ->
                    assertEquals("https://api.deepseek.com/chat/completions", request.url.toString())
                    requests += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    val (status, body) = responses[requests.lastIndex]
                    respond(body, HttpStatusCode.fromValue(status))
                },
            )
        val runtime =
            assertNotNull(
                AiConfigKoogBridge().createRuntime(
                    AppSettings.AiConfig(
                        type =
                            AppSettings.AiConfig.Type.OpenAI(
                                "https://api.deepseek.com",
                                "test-key",
                                "deepseek-flash",
                                "high",
                                "{\"seed\":7}",
                            ),
                    ),
                    KtorKoogHttpClient.Factory(baseClient = client),
                ),
            )
        val executor = AgentImageFallback(runtime.promptExecutor, images, this::prepare) { phases += it.phase }

        suspend fun prepare(
            media: UiMedia.Image,
            maxBytes: Int,
        ): AttachmentSource.Image {
            downloads++
            return prepare.invoke(media, maxBytes)
        }

        suspend fun execute(prompt: Prompt): String = executor.execute(prompt, runtime.model).textContent()

        override fun close() {
            executor.close()
            client.close()
        }
    }

    private class MemoryHistory : ChatHistoryProvider {
        var messages: List<Message> = emptyList()

        override suspend fun store(
            conversationId: String,
            messages: List<Message>,
        ) {
            this.messages = messages
        }

        override suspend fun load(conversationId: String): List<Message> = messages
    }

    private class EchoTool : SimpleTool<EchoTool.Args>(argsType = typeToken<Args>(), name = "echo", description = "Echo text") {
        var calls = 0

        @Serializable
        data class Args(
            val text: String,
        )

        override suspend fun execute(args: Args): String {
            calls++
            return args.text
        }
    }

    private fun JsonObject.imageUrls(): List<String> =
        getValue("messages").jsonArray.flatMap { message ->
            (message.jsonObject["content"] as? JsonArray).orEmpty().mapNotNull { part ->
                part.jsonObject["image_url"]
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.content
            }
        }

    private companion object {
        const val URL = "https://wx3.sinaimg.cn/large/001ZzMwgly1iii38t9sxrj60xm0wxkel02.jpg"
        const val INLINE = "data:image/jpeg;base64,AQID"
        val image = UiMedia.Image(URL, URL, null, 1185f, 1210f, false)
        val DOWNLOAD_ERROR =
            """
            {"error":{"message":".messages[1].image[0]: Failed to download image from $URL",
              "type":"invalid_request_error","code":"invalid_request_error"}}
            """.trimIndent()
        val SUCCESS =
            """
            {"id":"response","object":"chat.completion","created":0,"model":"deepseek-flash","choices":[{
              "index":0,"message":{"role":"assistant","content":"done"},"finish_reason":"stop"
            }]}
            """.trimIndent()

        fun errorBody(message: String): String = """{"error":{"message":"$message","type":"invalid_request_error"}}"""

        fun inlineImage(): AttachmentSource.Image =
            AttachmentSource.Image(AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3)), "jpg", "image/jpeg")

        fun prompt(vararg urls: String = arrayOf(URL)): Prompt =
            Prompt.build("image-test") {
                user {
                    text("Explain this post")
                    urls.forEach { image(it) }
                }
            }
    }
}
