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
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentIntermediateMessagesTest {
    @Test
    fun completedReplyIsStoredWhileTheNextToolIsStillRunningForEveryAgent() =
        runTest {
            for ((agentId, finishAfterToolResults) in listOf(
                "flare-generic-chat" to false,
                "flare-status-insight" to true,
                "flare-profile-insight" to false,
                "flare-local-history" to false,
            )) {
                withAgent(agentId = agentId, finishAfterToolResults = finishAfterToolResults) { fixture ->
                    val run = async { fixture.run() }
                    try {
                        fixture.tool.started.await()
                        assertFalse(run.isCompleted)
                        assertEquals(listOf("I will check that."), fixture.history.assistantText())

                        fixture.tool.finish.complete(Unit)
                        assertEquals("Here is the result.", run.await().getOrThrow())
                        assertEquals(listOf("I will check that.", "Here is the result."), fixture.history.assistantText())
                    } finally {
                        run.cancelAndJoin()
                    }
                }
            }
        }

    @Test
    fun failedFollowUpKeepsTheCompletedReply() =
        runTest {
            withAgent(failFollowUp = true) { fixture ->
                val run = async { fixture.run() }
                fixture.tool.started.await()
                fixture.tool.finish.complete(Unit)

                assertFailsWith<AgentRetryableException> { run.await().getOrThrow() }
                assertEquals(listOf("I will check that."), fixture.history.assistantText())
            }
        }

    @Test
    fun cancellationKeepsTheReplyAndDoesNotStartAnotherRequest() =
        runTest {
            withAgent { fixture ->
                val run = async { fixture.run() }
                fixture.tool.started.await()

                run.cancelAndJoin()

                assertTrue(run.isCancelled)
                assertEquals(1, fixture.calls)
                assertEquals(listOf("I will check that."), fixture.history.assistantText())
            }
        }

    @Test
    fun identicalRepliesInDifferentRoundsHaveDifferentIdentities() =
        runTest {
            withAgent(firstText = "Same answer.", finalText = "Same answer.") { fixture ->
                fixture.tool.finish.complete(Unit)
                fixture.run().getOrThrow()

                val replies = fixture.history.messages.filterIsInstance<Message.Assistant>()
                assertEquals(listOf("Same answer.", "Same answer."), fixture.history.assistantText())
                assertEquals(2, replies.map { assertNotNull(it.id) }.distinct().size)
            }
        }

    @Test
    fun toolOnlyResponseDoesNotCreateAnEmptyVisibleReply() =
        runTest {
            withAgent(firstText = null) { fixture ->
                val run = async { fixture.run() }
                try {
                    fixture.tool.started.await()
                    assertEquals(emptyList(), fixture.history.assistantText())
                    fixture.tool.finish.complete(Unit)
                    run.await().getOrThrow()
                    assertEquals(listOf("Here is the result."), fixture.history.assistantText())
                } finally {
                    run.cancelAndJoin()
                }
            }
        }

    @Test
    fun retryResumesSavedResultsWithoutRepeatingTheUserMessageOrCompletedTools() =
        runTest {
            for ((agentId, finishAfterTools) in listOf(
                "flare-generic-chat" to false,
                "flare-status-insight" to true,
                "flare-profile-insight" to false,
                "flare-local-history" to false,
            )) {
                withAgent(agentId = agentId, finishAfterToolResults = finishAfterTools, failFollowUp = true) { fixture ->
                    fixture.tool.finish.complete(Unit)
                    val failure = assertFailsWith<AgentRetryableException> { fixture.run().getOrThrow() }.toAgentFailure("Check that.")
                    assertEquals(if (finishAfterTools) AgentRetryMode.Finish else AgentRetryMode.Resume, failure.retryMode)
                    assertEquals("Here is the result.", fixture.run(failure.retryMode).getOrThrow())
                    assertEquals(1, fixture.tool.calls)
                    assertEquals(3, fixture.calls)
                    assertEquals(1, Regex("Check that\\.").findAll(fixture.requests.last()).count())
                    assertTrue(fixture.requests.last().contains("result"))
                    if (finishAfterTools) assertFalse(fixture.requests.last().contains("\"tools\""))
                }
            }
        }

    private suspend fun withAgent(
        agentId: String = "intermediate-test",
        finishAfterToolResults: Boolean = false,
        firstText: String? = "I will check that.",
        finalText: String = "Here is the result.",
        failFollowUp: Boolean = false,
        test: suspend (Fixture) -> Unit,
    ) {
        val fixture = Fixture(agentId, finishAfterToolResults, firstText, finalText, failFollowUp)
        try {
            test(fixture)
        } finally {
            fixture.close()
        }
    }

    private class Fixture(
        agentId: String,
        finishAfterToolResults: Boolean,
        firstText: String?,
        finalText: String,
        failFollowUp: Boolean,
    ) {
        val tool = WaitingTool()
        val history = MemoryHistory()
        var calls = 0
        val requests = mutableListOf<String>()
        private val client =
            HttpClient(
                MockEngine { request ->
                    requests += (request.body as TextContent).text
                    calls++
                    if (calls == 2 && failFollowUp) error("Model request failed")
                    val message =
                        if (calls == 1) {
                            """{"role":"assistant","content":${firstText?.let(
                                ::JsonPrimitive,
                            )},"tool_calls":[{"id":"call-1","type":"function","function":{"name":"wait","arguments":"{}"}}]}"""
                        } else {
                            """{"role":"assistant","content":${JsonPrimitive(finalText)}}"""
                        }
                    val finishReason = if (calls == 1) "tool_calls" else "stop"
                    respond(
                        """{"id":"response","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"message":$message,"finish_reason":"$finishReason"}]}""",
                    )
                },
            )
        private val runtime =
            assertNotNull(
                AiConfigKoogBridge().createRuntime(
                    AppSettings.AiConfig(
                        type = AppSettings.AiConfig.Type.OpenAI("https://agent.example/v1", "test-key", "test-model"),
                    ),
                    KtorKoogHttpClient.Factory(baseClient = client),
                ),
            )
        private val request =
            FlareAgentRequest(
                prompt = Prompt.build("input") { user("Check that.") },
                systemPrompt = "Use the wait tool, then answer.",
                agentId = agentId,
                strategyName = "intermediate-test",
                analyzeNodeName = "analyze",
                executeToolsNodeName = "tools",
                sendToolResultsNodeName = "results",
                finishAfterToolResults = finishAfterToolResults,
                toolSet = AgentToolSet.Empty.copy(toolRegistry = ToolRegistry { tool(tool) }),
            )

        suspend fun run(retryMode: AgentRetryMode? = null): Result<String> {
            val agent = runtime.createAgent(request.copy(retryMode = retryMode), onTrace = {}, chatHistoryProvider = history)
            return try {
                runCatching { agent.run(request.prompt, "conversation") }
            } finally {
                agent.close()
            }
        }

        suspend fun close() {
            runtime.promptExecutor.close()
            client.close()
        }
    }

    private class WaitingTool : SimpleTool<WaitingTool.Args>(typeToken<Args>(), "wait", "Wait for a result") {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()

        @Serializable
        class Args

        override suspend fun execute(args: Args): String {
            calls++
            started.complete(Unit)
            finish.await()
            return "result"
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

        fun assistantText(): List<String> =
            messages.filterIsInstance<Message.Assistant>().map { it.textContent() }.filter { it.isNotBlank() }
    }
}
