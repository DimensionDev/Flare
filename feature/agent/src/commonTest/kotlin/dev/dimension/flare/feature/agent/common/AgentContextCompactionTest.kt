package dev.dimension.flare.feature.agent.common

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.feature.agent.runtime.AiConfigKoogBridge
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentContextCompactionTest {
    @Test
    fun largeHistoryBecomesOneCheckpointAndRecentContextWithoutLosingTheCurrentRequest() =
        runTest {
            Fixture().use { fixture ->
                fixture.history.messages = oldHistory()
                assertEquals("Done", fixture.run())
                assertEquals(1, fixture.checkpoints.size)
                assertTrue(fixture.requests.first().isCompaction())
                assertFalse(fixture.requests.first().hasTools())
                val continued = fixture.requests.last().toString()
                assertTrue("checkpoint-1" in continued)
                assertTrue("CURRENT USER REQUIREMENT" in continued)
                assertFalse("old-0" in continued)
                assertFalse(fixture.progress.flatten().any { "Create a concise conversation checkpoint" in it.textContent() })
                assertEquals(
                    "Done",
                    fixture.history.messages
                        .last()
                        .textContent(),
                )
            }
        }

    @Test
    fun shortHistoryKeepsAllMessagesAndRefreshesSystemInstructions() =
        runTest {
            Fixture().use { fixture ->
                fixture.history.messages =
                    Prompt
                        .build("old") {
                            system("OBSOLETE SYSTEM")
                            user("Prior requirement")
                            assistant("Prior answer")
                        }.messages
                assertEquals("Done", fixture.run())
                assertEquals(1, fixture.requests.size)
                assertTrue(
                    fixture.requests
                        .single()
                        .toString()
                        .contains("CURRENT SYSTEM"),
                )
                assertFalse(
                    fixture.requests
                        .single()
                        .toString()
                        .contains("OBSOLETE SYSTEM"),
                )
                assertTrue(
                    fixture.requests
                        .single()
                        .toString()
                        .contains("Prior requirement"),
                )
                assertTrue(fixture.checkpoints.isEmpty())
            }
        }

    @Test
    fun repeatedCompactionDoesNotResetTheIterationLimitOrExecuteAToolTwice() =
        runTest {
            Fixture(maxIterations = 11, resultSize = 18_000) { request, number ->
                when {
                    request.isCompaction() -> textResponse("checkpoint-$number: continue remaining work")
                    !request.hasTools() -> textResponse("Step limit reached; ask to continue")
                    else -> toolResponse(number)
                }
            }.use { fixture ->
                assertTrue(fixture.run().contains("Step limit"))
                assertEquals(4, fixture.toolCalls)
                assertTrue(fixture.checkpoints.size >= 2)
                assertTrue(fixture.phases.count { it == AgentPhase.StepCompleted } <= 11)
                assertTrue(
                    fixture.requests
                        .last()
                        .toString()
                        .contains("step limit"),
                )
                assertTrue(
                    fixture.requests
                        .filter { it.isCompaction() }
                        .drop(1)
                        .all { "checkpoint-" in it.toString() },
                )
                assertTrue(fixture.requests.filter { it.isCompaction() }.all { "CURRENT USER REQUIREMENT" in it.toString() })
            }
        }

    @Test
    fun compactionWaitsForTheWholeToolBatchAndKeepsCompletedResults() =
        runTest {
            Fixture(resultSize = 9_000) { request, number ->
                when {
                    request.isCompaction() -> textResponse("Batch completed")
                    number == 1 -> toolResponse(number, 2)
                    else -> textResponse("Done")
                }
            }.use { fixture ->
                assertEquals("Done", fixture.run())
                assertEquals(2, fixture.toolCalls)
                val summary = fixture.requests.single { it.isCompaction() }.toString()
                assertTrue("Completed call-1-1" in summary)
                assertTrue("Completed call-1-2" in summary)
                assertEquals(2, fixture.callsWhenCompacting.single())
            }
        }

    @Test
    fun overflowRetriesOnlyTheModelOnceAndKeepsTheToolResult() =
        runTest {
            var failures = 0
            Fixture(resultSize = 4_000) { request, number ->
                when {
                    request.isCompaction() -> textResponse("Completed call-1-1; waiting for the answer")
                    number == 1 -> toolResponse(number)
                    failures++ == 0 -> OVERFLOW
                    else -> textResponse("Recovered")
                }
            }.use { fixture ->
                assertEquals("Recovered", fixture.run())
                assertEquals(1, fixture.toolCalls)
                assertEquals(3, fixture.requests.size)
                assertTrue(fixture.requests.none { it.isCompaction() })
                assertTrue(fixture.checkpoints.isEmpty())
                assertTrue(
                    fixture.requests
                        .last()
                        .toString()
                        .length < fixture.requests[1].toString().length,
                )
                assertTrue("Completed call-1-1" in fixture.requests.last().toString())
            }
        }

    @Test
    fun repeatedOverflowStopsAfterOneRecoveryAttempt() =
        runTest {
            Fixture(resultSize = 4_000) { request, number ->
                when {
                    request.isCompaction() -> textResponse("Completed tool; work remains")
                    number == 1 -> toolResponse(number)
                    else -> OVERFLOW
                }
            }.use { fixture ->
                assertFailsWith<Exception> { fixture.run() }
                assertEquals(1, fixture.toolCalls)
                assertEquals(3, fixture.requests.size)
                assertTrue(fixture.checkpoints.isEmpty())
            }
        }

    @Test
    fun failedOrCancelledSummaryDoesNotReplaceThePreviousCheckpoint() =
        runTest {
            for (failure in listOf("cancel", "error", "auth", "empty", "reasoning", "tool", "oversized", "truncated", "filtered")) {
                Fixture { request, _ ->
                    check(request.isCompaction())
                    when (failure) {
                        "cancel" -> {
                            throw CancellationException("cancelled compaction")
                        }

                        "error" -> {
                            ERROR
                        }

                        "auth" -> {
                            UNAUTHORIZED
                        }

                        "reasoning" -> {
                            textResponse(
                                "",
                            ).replace("\"content\":\"\"", "\"content\":null,\"reasoning_content\":\"Thinking only\"")
                        }

                        "tool" -> {
                            toolResponse(99)
                        }

                        "oversized" -> {
                            textResponse("x".repeat(30_000))
                        }

                        "truncated" -> {
                            textResponse("Only part of the checkpoint").replace("\"stop\"", "\"length\"")
                        }

                        "filtered" -> {
                            textResponse("Filtered checkpoint").replace("\"stop\"", "\"content_filter\"")
                        }

                        else -> {
                            textResponse("")
                        }
                    }
                }.use { fixture ->
                    val previous = listOf(agentContextSummary("Previous checkpoint")) + oldHistory()
                    fixture.history.messages = previous
                    assertFailsWith<Exception> { fixture.run() }
                    assertEquals(previous, fixture.history.messages.filter { it.id in previous.map(Message::id) })
                    assertTrue(fixture.checkpoints.isEmpty())
                    assertEquals(0, fixture.toolCalls)
                    assertEquals(1, fixture.requests.size)
                }
            }
        }

    @Test
    fun checkpointPersistenceFailureDoesNotContinueOrSavePartialReplacement() =
        runTest {
            Fixture(failCheckpoint = true).use { fixture ->
                val original = oldHistory()
                fixture.history.messages = original
                assertFailsWith<AgentRetryableException> { fixture.run() }
                assertEquals(original, fixture.history.messages.filter { it.id in original.map(Message::id) })
                assertEquals(1, fixture.requests.size)
                assertTrue(fixture.checkpoints.isEmpty())
            }
        }

    @Test
    fun singleToolTurnStillFinishesNormallyAfterCompaction() =
        runTest {
            Fixture(resultSize = 18_000) { request, number ->
                when {
                    request.isCompaction() -> textResponse("Tool completed")
                    number == 1 -> toolResponse(number)
                    else -> textResponse("Finished insight")
                }
            }.use { fixture ->
                assertEquals("Finished insight", fixture.run(finishAfterToolResults = true))
                assertEquals(1, fixture.toolCalls)
                assertEquals(1, fixture.checkpoints.size)
                assertFalse(fixture.requests.last().hasTools())
                assertFalse("step limit" in fixture.requests.last().toString())
            }
        }

    @Test
    fun recentToolCallsAndResultsStayPairedAfterCompaction() =
        runTest {
            Fixture().use { fixture ->
                val call =
                    (Prompt.build("tool") { assistant("") }.messages.single() as Message.Assistant)
                        .copy(id = "call-message", parts = listOf(MessagePart.Tool.Call("paired-call", "work", "{}")))
                val result =
                    Prompt
                        .build("result") {
                            user {
                                toolResult(MessagePart.Tool.Result("paired-call", "work", "Completed exactly once"))
                            }
                        }.messages
                        .single()
                        .withAgentMessageId("result-message")
                fixture.history.messages = oldHistory() + call + result
                fixture.run()
                val replay = fixture.history.messages.flatMap { it.parts }
                assertEquals(listOf("paired-call"), replay.filterIsInstance<MessagePart.Tool.Call>().map { it.id })
                assertEquals(listOf("paired-call"), replay.filterIsInstance<MessagePart.Tool.Result>().map { it.id })
                assertEquals(0, fixture.toolCalls)
            }
        }

    @Test
    fun providerTokenUsageTriggersCompactionBeforeTheNextToolRound() =
        runTest {
            Fixture { request, number ->
                when {
                    request.isCompaction() -> {
                        textResponse("Updated progress")
                    }

                    number == 1 -> {
                        toolResponse(number).dropLast(1) +
                            ",\"usage\":{\"prompt_tokens\":9900,\"completion_tokens\":20,\"total_tokens\":9920}}"
                    }

                    else -> {
                        textResponse("Done")
                    }
                }
            }.use { fixture ->
                fixture.history.messages = oldHistory().take(3)
                assertEquals("Done", fixture.run())
                assertFalse(fixture.requests.first().isCompaction())
                assertEquals(1, fixture.checkpoints.size)
                assertEquals(1, fixture.toolCalls)
            }
        }

    @Test
    fun summaryHasItsOwnOutputBudgetWithoutChangingNormalRequests() =
        runTest {
            Fixture(resultSize = 28_000, extraBody = "{\"max_tokens\":17}") { request, number ->
                when {
                    request.isCompaction() -> textResponse("Completed the tool; work remains")
                    number == 1 -> toolResponse(number)
                    else -> textResponse("Recovered")
                }
            }.use { fixture ->
                assertEquals("Recovered", fixture.run())
                val summary = fixture.requests.single { it.isCompaction() }
                val outputBudget = (summary["max_completion_tokens"] ?: summary["max_tokens"])?.jsonPrimitive?.content?.toInt()
                assertTrue(assertNotNull(outputBudget) > 17)
                assertTrue(fixture.requests.filterNot { it.isCompaction() }.all { it["max_tokens"]?.jsonPrimitive?.content == "17" })
            }
        }

    @Test
    fun largeNormalOutputReservationDoesNotPreventSummarizingHistoryThatFitsTheModel() =
        runTest {
            Fixture(extraBody = "{\"max_tokens\":8000}").use { fixture ->
                fixture.history.messages = oldHistory()
                assertEquals("Done", fixture.run())
                assertEquals(1, fixture.checkpoints.size)
                assertTrue(fixture.requests.first().isCompaction())
                assertEquals(
                    "8000",
                    fixture.requests
                        .last()["max_tokens"]
                        ?.jsonPrimitive
                        ?.content,
                )
            }
        }

    @Test
    fun failedSummaryPreservesCompletedToolsWhenTheAgentIsRecreated() =
        runTest {
            for (cancelled in listOf(false, true)) {
                var failed = false
                Fixture(resultSize = 18_000) { request, number ->
                    when {
                        number == 1 -> {
                            toolResponse(number)
                        }

                        request.isCompaction() && !failed -> {
                            failed = true
                            if (cancelled) throw CancellationException("Cancelled after tools completed")
                            ERROR
                        }

                        request.isCompaction() -> {
                            textResponse("The tool completed; only a reply remains")
                        }

                        else -> {
                            textResponse("Recovered without repeating the tool")
                        }
                    }
                }.use { fixture ->
                    fixture.history.messages = listOf(agentContextSummary("EXISTING CHECKPOINT"))
                    assertFailsWith<Exception> { fixture.run() }
                    val saved = fixture.history.messages.flatMap { it.parts }
                    assertEquals(1, saved.filterIsInstance<MessagePart.Tool.Call>().size)
                    assertEquals(1, saved.filterIsInstance<MessagePart.Tool.Result>().size)
                    assertTrue(fixture.checkpoints.isEmpty())
                    val resumeIndex = fixture.requests.size
                    assertEquals("Recovered without repeating the tool", fixture.run())
                    val resumed = fixture.requests[resumeIndex].toString()
                    assertTrue("EXISTING CHECKPOINT" in resumed)
                    assertTrue("Tool work, call call-1-1" in resumed)
                    assertTrue("Completed call-1-1" in resumed)
                    assertEquals(1, fixture.toolCalls)
                }
            }
        }

    @Test
    fun summaryOverflowRecoversWithoutAnotherSummaryOrReplacingTheCheckpoint() =
        runTest {
            Fixture { request, _ -> if (request.isCompaction()) OVERFLOW else textResponse("Recovered") }.use { fixture ->
                val original = listOf(agentContextSummary("PREVIOUS CHECKPOINT")) + oldHistory()
                fixture.history.messages = original
                assertEquals("Recovered", fixture.run())
                assertEquals(2, fixture.requests.size)
                assertTrue(fixture.requests.first().isCompaction())
                assertFalse(fixture.requests.last().isCompaction())
                assertTrue("PREVIOUS CHECKPOINT" in fixture.requests.last().toString())
                assertTrue("CURRENT USER REQUIREMENT" in fixture.requests.last().toString())
                assertTrue(fixture.checkpoints.isEmpty())
                original.forEach { message -> assertTrue(message in fixture.history.messages) }
            }
        }

    @Test
    fun oversizedPlainTextHistoryCanRecoverWithoutSendingAnOversizedSummary() =
        runTest {
            Fixture { _, number -> if (number == 1) OVERFLOW else textResponse("Recovered") }.use { fixture ->
                val original = Prompt.build("history") { assistant("OLD LARGE ANSWER " + "x".repeat(60_000)) }.messages
                fixture.history.messages = original
                assertEquals("Recovered", fixture.run())
                assertEquals(2, fixture.requests.size)
                assertTrue(fixture.requests.none { it.isCompaction() })
                assertFalse("OLD LARGE ANSWER" in fixture.requests.last().toString())
                assertTrue(fixture.history.messages.any { it.parts == original.single().parts })
            }
        }

    @Test
    fun unknownWindowFallbackDoesNotRejectHistoryThatTheProviderAccepts() =
        runTest {
            Fixture(contextWindow = null).use { fixture ->
                val original = Prompt.build("history") { assistant("LARGE ANSWER " + "x".repeat(30_000)) }.messages
                fixture.history.messages = original
                assertEquals("Done", fixture.run())
                assertEquals(1, fixture.requests.size)
                assertFalse(fixture.requests.single().isCompaction())
                assertTrue("LARGE ANSWER" in fixture.requests.single().toString())
                assertTrue(fixture.checkpoints.isEmpty())
            }
        }

    @Test
    fun unknownWindowRecoveryUsesTheRejectedRequestSizeInsteadOfAFakeHardLimit() =
        runTest {
            Fixture(contextWindow = null) { _, number -> if (number == 1) OVERFLOW else textResponse("Recovered") }.use { fixture ->
                fixture.history.messages = Prompt.build("history") { assistant("old " + "x".repeat(60_000)) }.messages
                val currentRequest = "CURRENT LARGE REQUIREMENT " + "y".repeat(20_000)
                assertEquals("Recovered", fixture.run(userText = currentRequest))
                assertEquals(2, fixture.requests.size)
                assertTrue(currentRequest in fixture.requests.last().toString())
                assertTrue(fixture.checkpoints.isEmpty())
            }
        }

    @Test
    fun ruleRecoveryKeepsTheWholeToolBatchAndSavesOriginalResults() =
        runTest {
            Fixture(resultSize = 4_000) { _, number ->
                when (number) {
                    1 -> toolResponse(number, 2)
                    2 -> OVERFLOW
                    else -> textResponse("Recovered")
                }
            }.use { fixture ->
                assertEquals("Recovered", fixture.run())
                assertEquals(2, fixture.toolCalls)
                assertEquals(3, fixture.requests.size)
                val retry = fixture.requests.last().toString()
                assertTrue("CURRENT USER REQUIREMENT" in retry)
                assertTrue("call-1-1" in retry && "call-1-2" in retry)
                assertTrue("Completed call-1-1" in retry && "Completed call-1-2" in retry)
                assertFalse("x".repeat(4_000) in retry)
                val stored =
                    fixture.history.messages
                        .flatMap { it.parts }
                        .filterIsInstance<MessagePart.Tool.Result>()
                assertEquals(2, stored.size)
                assertTrue(stored.all { it.output.endsWith("x".repeat(4_000)) })
                assertTrue(fixture.checkpoints.isEmpty())
            }
        }

    @Test
    fun recoveryIsSmallerThanTheAlreadyShortenedSummaryThatWasRejected() =
        runTest {
            Fixture(contextWindow = null) { request, _ ->
                if (request.isCompaction()) OVERFLOW else textResponse("Recovered")
            }.use { fixture ->
                fixture.history.messages =
                    (1..8).flatMap { index ->
                        Prompt
                            .build("batch-$index") {
                                assistant { toolCall(MessagePart.Tool.Call("old-$index", "work", "{\"id\":\"old-$index\"}")) }
                                user { toolResult(MessagePart.Tool.Result("old-$index", "work", "Completed " + "x".repeat(30_000))) }
                            }.messages
                    }
                assertEquals("Recovered", fixture.run())
                assertEquals(2, fixture.requests.size)
                assertTrue(fixture.requests.first().isCompaction())
                assertTrue(
                    fixture.requests
                        .last()
                        .toString()
                        .length <
                        fixture.requests
                            .first()
                            .toString()
                            .length,
                )
                assertTrue(fixture.checkpoints.isEmpty())
                assertEquals(0, fixture.toolCalls)
            }
        }

    @Test
    fun unknownWindowDeclinesAnOversizedReplacementWithoutRejectingTheNormalRequest() =
        runTest {
            var summaries = 0
            var normalRequests = 0
            Fixture(contextWindow = null, resultSize = 1_100) { request, number ->
                when {
                    request.isCompaction() -> textResponse(if (++summaries == 1) "A".repeat(1_050) else "B".repeat(1_400))
                    ++normalRequests == 1 -> toolResponse(number)
                    else -> textResponse("Done")
                }
            }.use { fixture ->
                fixture.history.messages = oldHistory().take(2)
                assertEquals("Done", fixture.run(userText = "Current requirement " + "x".repeat(6_000)))
                assertEquals(2, summaries)
                assertEquals(1, fixture.checkpoints.size)
                assertTrue("A".repeat(1_050) in fixture.requests.last().toString())
                assertFalse("B".repeat(1_400) in fixture.requests.last().toString())
            }
        }

    @Test
    fun recoveryDoesNotRetryAnUnchangedRequestOrDiscardTheCurrentRequest() =
        runTest {
            for (currentRequest in listOf("Keep this requirement", "x".repeat(30_000))) {
                Fixture { _, _ -> OVERFLOW }.use { fixture ->
                    assertFailsWith<Exception> { fixture.run(userText = currentRequest) }
                    assertEquals(1, fixture.requests.size)
                    assertTrue(currentRequest in fixture.requests.single().toString())
                    assertTrue(fixture.checkpoints.isEmpty())
                    assertEquals(0, fixture.toolCalls)
                }
            }
        }

    @Test
    fun cancelledRecoveryRestoresOriginalResultsAndDoesNotRetryAgain() =
        runTest {
            Fixture(resultSize = 4_000) { _, number ->
                when (number) {
                    1 -> toolResponse(number)
                    2 -> OVERFLOW
                    else -> throw CancellationException("Cancelled recovery")
                }
            }.use { fixture ->
                assertFailsWith<CancellationException> { fixture.run() }
                assertEquals(3, fixture.requests.size)
                assertEquals(1, fixture.toolCalls)
                val stored =
                    fixture.history.messages
                        .flatMap { it.parts }
                        .filterIsInstance<MessagePart.Tool.Result>()
                assertTrue(stored.single().output.endsWith("x".repeat(4_000)))
                assertTrue(fixture.checkpoints.isEmpty())
            }
        }

    internal class Fixture(
        private val maxIterations: Int = 256,
        private val resultSize: Int = 20,
        private val failCheckpoint: Boolean = false,
        private val extraBody: String = "",
        private val contextWindow: Int? = 16_384,
        private val response: (
            JsonObject,
            Int,
        ) -> String = { request, _ -> if (request.isCompaction()) textResponse("checkpoint-1: remaining work") else textResponse("Done") },
    ) : AutoCloseable {
        val requests = mutableListOf<JsonObject>()
        val checkpoints = mutableListOf<AgentContextCheckpoint>()
        val progress = mutableListOf<List<Message>>()
        val phases = mutableListOf<AgentPhase>()
        val callsWhenCompacting = mutableListOf<Int>()
        val history = History()
        var toolCalls = 0
        private val tool =
            object : SimpleTool<Args>(typeToken<Args>(), "work", "Perform one operation") {
                override suspend fun execute(args: Args): String {
                    toolCalls++
                    return "Completed ${args.id}: " + "x".repeat(resultSize)
                }
            }
        val client =
            HttpClient(
                MockEngine { request ->
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    requests += body
                    if (body.isCompaction()) callsWhenCompacting += toolCalls
                    val content = response(body, requests.size)
                    respond(
                        content,
                        when (content) {
                            OVERFLOW, ERROR -> HttpStatusCode.BadRequest
                            UNAUTHORIZED -> HttpStatusCode.Unauthorized
                            else -> HttpStatusCode.OK
                        },
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            )
        val runtime =
            assertNotNull(
                AiConfigKoogBridge().createRuntime(
                    AppSettings.AiConfig(
                        type =
                            AppSettings.AiConfig.Type.OpenAI(
                                "https://agent.example/v1/",
                                "key",
                                "test-model",
                                contextWindow = contextWindow,
                                extraBody = extraBody,
                            ),
                    ),
                    KtorKoogHttpClient.Factory(baseClient = client),
                ),
            )

        suspend fun run(
            finishAfterToolResults: Boolean = false,
            historyProvider: ChatHistoryProvider = history,
            userText: String = "CURRENT USER REQUIREMENT: finish the task",
        ): String {
            val request =
                FlareAgentRequest(
                    prompt = Prompt.build("test") { user(userText) },
                    systemPrompt = "CURRENT SYSTEM",
                    agentId = "test",
                    strategyName = "test",
                    analyzeNodeName = "analyze",
                    executeToolsNodeName = "tools",
                    sendToolResultsNodeName = "results",
                    maxIterations = maxIterations,
                    finishAfterToolResults = finishAfterToolResults,
                    toolSet = AgentToolSet.Empty.copy(toolRegistry = ToolRegistry { tool(tool) }),
                )
            val agent =
                runtime.createAgent(
                    request,
                    { phases += it.phase },
                    historyProvider,
                    storeMessages = { id, messages ->
                        progress += messages
                        historyProvider.store(id, messages)
                    },
                    storeCheckpoint = { _, _, checkpoint ->
                        check(!failCheckpoint) { "persistence failed" }
                        checkpoints += checkpoint
                    },
                )
            return try {
                agent.run(request.prompt, "conversation")
            } finally {
                agent.close()
            }
        }

        override fun close() {
            runtime.promptExecutor.close()
            client.close()
        }
    }

    internal class History : ChatHistoryProvider {
        var messages: List<Message> = emptyList()

        override suspend fun store(
            conversationId: String,
            messages: List<Message>,
        ) {
            this.messages = messages
        }

        override suspend fun load(conversationId: String): List<Message> = messages
    }

    @Serializable private data class Args(
        val id: String,
    )

    private companion object {
        const val OVERFLOW = """{"error":{"code":"context_length_exceeded","message":"maximum context length exceeded"}}"""
        const val ERROR = """{"error":{"message":"Invalid parameter"}}"""
        const val UNAUTHORIZED = """{"error":{"message":"Invalid API key"}}"""

        fun oldHistory(): List<Message> =
            (0..7).map { i ->
                Prompt
                    .build("old-$i") { user("old-$i " + "x".repeat(2_300)) }
                    .messages
                    .single()
                    .withAgentMessageId("old-$i")
            }

        fun JsonObject.isCompaction(): Boolean = toString().contains("Create a concise conversation checkpoint")

        fun JsonObject.hasTools(): Boolean = (get("tools") as? JsonArray).orEmpty().isNotEmpty()

        fun textResponse(text: String): String =
            """{"id":"response","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":${Json.encodeToString(
                text,
            )}},"finish_reason":"stop"}]}"""

        fun toolResponse(
            number: Int,
            count: Int = 1,
        ): String {
            val calls =
                (1..count).joinToString(",") { i ->
                    """{"id":"call-$number-$i","type":"function","function":{"name":"work","arguments":${Json.encodeToString(
                        "{\"id\":\"call-$number-$i\"}",
                    )}}}"""
                }
            return """
                {"id":"response","object":"chat.completion","created":0,"model":"test-model","choices":[{
                    "index":0,"message":{"role":"assistant","content":null,"tool_calls":[$calls]},"finish_reason":"tool_calls"
                }]}
                """.trimIndent()
        }
    }
}
