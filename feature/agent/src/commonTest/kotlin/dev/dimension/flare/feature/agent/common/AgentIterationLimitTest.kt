package dev.dimension.flare.feature.agent.common

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.feature.agent.runtime.AiConfigKoogBridge
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentIterationLimitTest {
    @Test
    fun defaultBudgetContinuesPast32StepsAndSavesSummaryBefore256() =
        runTest {
            Fixture().use { fixture ->
                assertEquals(SUMMARY, fixture.run())
                assertEquals(256, fixture.request.maxIterations)
                assertEquals(126, fixture.tool.calls)
                assertEquals(127, fixture.requests.size)
                assertEquals(255, fixture.phases.count { it == AgentPhase.StepCompleted })
                assertTrue(fixture.requests.dropLast(1).all { it.hasTools() })
                fixture.assertSummarySaved()
            }
        }

    @Test
    fun oddAndEvenBudgetsReserveEnoughStepsToFinish() =
        runTest {
            for (budget in listOf(3, 4, 5, 6, 7, 8)) {
                Fixture(maxIterations = budget).use { fixture ->
                    assertEquals(SUMMARY, fixture.run())
                    assertEquals((budget - 3) / 2, fixture.tool.calls)
                    assertTrue(fixture.phases.count { it == AgentPhase.StepCompleted } <= budget)
                    fixture.assertSummarySaved()
                }
            }
        }

    @Test
    fun finalToolBatchFinishesBeforeSummarizing() =
        runTest {
            Fixture(maxIterations = 5) { request, index ->
                if (request.hasTools()) toolResponse(index, count = 2) else textResponse(SUMMARY)
            }.use { fixture ->
                assertEquals(SUMMARY, fixture.run())
                assertEquals(2, fixture.tool.calls)
                val summaryRequest = fixture.requests.last().toString()
                assertTrue(summaryRequest.contains("Completed result-1-1"))
                assertTrue(summaryRequest.contains("Completed result-1-2"))
                fixture.assertSummarySaved()
            }
        }

    @Test
    fun nextUserInputCanUseToolsAgainWithoutThePreviousLimitInstruction() =
        runTest {
            Fixture(maxIterations = 7).use { fixture ->
                assertEquals(SUMMARY, fixture.run())
                val nextRequest = fixture.requests.size
                assertEquals(SUMMARY, fixture.run())
                assertEquals(4, fixture.tool.calls)
                assertEquals(2, fixture.history.stores)
                val resumed = fixture.requests[nextRequest]
                assertTrue(resumed.hasTools())
                assertTrue(resumed.toString().contains(SUMMARY))
                assertFalse(resumed.toString().contains(LIMIT_INSTRUCTION))
                fixture.assertSummarySaved()
            }
        }

    @Test
    fun naturalCompletionKeepsTheNormalResponseAndHistory() =
        runTest {
            Fixture { _, index ->
                if (index == 1) toolResponse(index) else textResponse("Finished normally")
            }.use { fixture ->
                assertEquals("Finished normally", fixture.run())
                assertEquals(1, fixture.tool.calls)
                assertEquals(1, fixture.history.stores)
                assertTrue(fixture.requests.all { it.hasTools() })
                assertFalse(fixture.requests.any { it.toString().contains(LIMIT_INSTRUCTION) })
                assertEquals(
                    "Finished normally",
                    fixture.history.messages
                        .last()
                        .textContent(),
                )
            }
        }

    @Test
    fun singleToolTurnPolicyStillFinishesWithoutALimitWarning() =
        runTest {
            Fixture().use { fixture ->
                assertEquals(SUMMARY, fixture.run(fixture.request.copy(finishAfterToolResults = true)))
                assertEquals(1, fixture.tool.calls)
                assertEquals(2, fixture.requests.size)
                assertFalse(fixture.requests.last().hasTools())
                assertFalse(
                    fixture.requests
                        .last()
                        .toString()
                        .contains(LIMIT_INSTRUCTION),
                )
                assertEquals(1, fixture.history.stores)
            }
        }

    @Test
    fun summaryCannotExecuteToolsEvenIfTheProviderReturnsAToolCall() =
        runTest {
            Fixture(maxIterations = 5) { _, index -> toolResponse(index) }.use { fixture ->
                assertFailsWith<Exception> { fixture.run() }
                assertEquals(1, fixture.tool.calls)
                assertEquals(2, fixture.requests.size)
                assertFalse(fixture.requests.last().hasTools())
                assertEquals(0, fixture.history.stores)
            }
        }

    @Test
    fun cancellationWhileSummarizingDoesNotRetryOrSaveAnAnswer() =
        runTest {
            Fixture(maxIterations = 5) { request, index ->
                if (!request.hasTools()) throw CancellationException("cancelled summary")
                toolResponse(index)
            }.use { fixture ->
                assertFailsWith<CancellationException> { fixture.run() }
                assertEquals(1, fixture.tool.calls)
                assertEquals(2, fixture.requests.size)
                assertEquals(0, fixture.history.stores)
            }
        }

    private class Fixture(
        maxIterations: Int? = null,
        response: (JsonObject, Int) -> String = { request, index ->
            if (request.hasTools()) toolResponse(index) else textResponse(SUMMARY)
        },
    ) : AutoCloseable {
        val requests = mutableListOf<JsonObject>()
        val phases = mutableListOf<AgentPhase>()
        val history = MemoryHistory()
        val tool = EchoTool()
        private val client =
            HttpClient(
                MockEngine { request ->
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    requests += body
                    respond(
                        response(body, requests.size),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            )
        private val runtime =
            assertNotNull(
                AiConfigKoogBridge().createRuntime(
                    AppSettings.AiConfig(type = AppSettings.AiConfig.Type.OpenAI("https://agent.example/v1/", "test-key", "test-model")),
                    KtorKoogHttpClient.Factory(baseClient = client),
                ),
            )
        val request =
            FlareAgentRequest(
                prompt = Prompt.build("iteration-test") { user("Continue working") },
                systemPrompt = "Use echo until the task is complete.",
                agentId = "iteration-test",
                strategyName = "iteration-test",
                analyzeNodeName = "analyze",
                executeToolsNodeName = "tools",
                sendToolResultsNodeName = "results",
                toolSet = AgentToolSet.Empty.copy(toolRegistry = ToolRegistry { tool(tool) }),
            ).let { request -> maxIterations?.let { request.copy(maxIterations = it) } ?: request }

        suspend fun run(request: FlareAgentRequest = this.request): String {
            val agent = runtime.createAgent(request, { phases += it.phase }, history)
            return try {
                agent.run(request.prompt, "conversation")
            } finally {
                agent.close()
            }
        }

        fun assertSummarySaved() {
            assertFalse(requests.last().hasTools())
            assertTrue(requests.last().toString().contains(LIMIT_INSTRUCTION))
            assertEquals(SUMMARY, history.messages.last().textContent())
            assertFalse(Json.encodeToString(history.messages).contains(LIMIT_INSTRUCTION))
            assertTrue(AgentPhase.AgentCompleted in phases)
            assertFalse(AgentPhase.AgentFailed in phases)
        }

        override fun close() {
            runtime.promptExecutor.close()
            client.close()
        }
    }

    private class MemoryHistory : ChatHistoryProvider {
        var stores = 0
        var messages: List<Message> = emptyList()

        override suspend fun store(
            conversationId: String,
            messages: List<Message>,
        ) {
            stores++
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
            return "Completed ${args.text}"
        }
    }

    private companion object {
        const val SUMMARY = "Work saved. More work remains; ask me to continue."
        const val LIMIT_INSTRUCTION = "step limit"

        fun JsonObject.hasTools(): Boolean = (get("tools") as? JsonArray).orEmpty().isNotEmpty()

        fun textResponse(text: String): String =
            """
            {"id":"response","object":"chat.completion","created":0,"model":"test-model","choices":[{
                "index":0,"message":{"role":"assistant","content":"$text"},"finish_reason":"stop"
            }]}
            """.trimIndent()

        fun toolResponse(
            index: Int,
            count: Int = 1,
        ): String {
            val calls =
                (1..count).joinToString(",") { call ->
                    """
                    {"id":"call-$index-$call","type":"function","function":{
                        "name":"echo","arguments":"{\"text\":\"result-$index-$call\"}"
                    }}
                    """.trimIndent()
                }
            return """
                {"id":"response-$index","object":"chat.completion","created":0,"model":"test-model","choices":[{
                    "index":0,"message":{"role":"assistant","content":null,"tool_calls":[$calls]},"finish_reason":"tool_calls"
                }]}
                """.trimIndent()
        }
    }
}
