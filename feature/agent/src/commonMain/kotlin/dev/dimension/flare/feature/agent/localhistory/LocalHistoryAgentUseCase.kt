package dev.dimension.flare.feature.agent.localhistory

import ai.koog.prompt.Prompt
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentConversationAttachment
import dev.dimension.flare.feature.agent.common.AgentConversationEvent
import dev.dimension.flare.feature.agent.common.AgentRunResult
import dev.dimension.flare.feature.agent.common.AgentToolContext
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.common.FlareAgentRequest
import dev.dimension.flare.feature.agent.common.FlareAgentRunner
import dev.dimension.flare.feature.agent.common.FlareAgentUnavailableException
import dev.dimension.flare.feature.agent.common.resolveAgentVisibleResult
import dev.dimension.flare.feature.agent.runtime.AgentAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single

@Serializable
public enum class LocalHistoryAgentTarget(
    public val routeValue: String,
) {
    Posts("posts"),
    Users("users"),
    All("all"),
    ;

    public companion object {
        public fun fromRouteValue(value: String?): LocalHistoryAgentTarget =
            entries.firstOrNull { target ->
                target.routeValue == value || target.name.equals(value, ignoreCase = true)
            } ?: All
    }
}

@Single
internal class LocalHistoryAgentUseCase(
    private val agentRunner: FlareAgentRunner,
    private val chatHistoryProvider: AgentChatHistoryProvider,
) {
    operator fun invoke(
        query: String?,
        target: LocalHistoryAgentTarget,
        searchDataSources: List<AccountMicroblogDataSource>,
        userInput: String? = null,
        conversationId: String,
    ): Flow<AgentConversationEvent<Unit, AgentTrace>> =
        channelFlow {
            run(
                query = query,
                target = target,
                searchDataSources = searchDataSources,
                userInput = userInput,
                conversationId = conversationId,
            )
        }

    private suspend fun SendChannel<AgentConversationEvent<Unit, AgentTrace>>.run(
        query: String?,
        target: LocalHistoryAgentTarget,
        searchDataSources: List<AccountMicroblogDataSource>,
        userInput: String?,
        conversationId: String,
    ) {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }
        val userInputValue = userInput?.trim().orEmpty()
        if (userInputValue.isBlank()) {
            agentRunner.clearConversation(conversationId)
        }
        val result =
            try {
                agentRunner.run(
                    request =
                        FlareAgentRequest(
                            prompt =
                                toLocalHistoryPrompt(
                                    query = normalizedQuery,
                                    target = target,
                                    userInput = userInputValue,
                                ),
                            systemPrompt = LOCAL_HISTORY_SYSTEM_PROMPT,
                            agentId = "flare-local-history",
                            strategyName = "local_history_chat",
                            analyzeNodeName = "answer_local_history_request",
                            executeToolsNodeName = "execute_local_history_tools",
                            sendToolResultsNodeName = "send_local_history_tool_results",
                            toolContext =
                                AgentToolContext(
                                    searchDataSources = searchDataSources,
                                ),
                            temperature = 0.3,
                            maxIterations = MAX_AGENT_ITERATIONS,
                            finishAfterToolResults = false,
                            chatMemoryWindowSize = CHAT_MEMORY_WINDOW_SIZE,
                        ),
                    conversationId = conversationId,
                ) { trace ->
                    send(AgentConversationEvent.Trace(trace))
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (throwable is FlareAgentUnavailableException) {
                    throw LocalHistoryAgentUnavailableException(throwable.availability)
                }
                throw throwable
            }

        val attachments = result.attachments.distinctBy { it.attachmentIdentity() }
        val visibleResult = resolveAgentVisibleResult(result.text, result.inputRequest)
        chatHistoryProvider.storeAssistantAttachments(conversationId, attachments)
        visibleResult.inputRequest?.let { inputRequest ->
            chatHistoryProvider.storeAssistantInputRequest(conversationId, inputRequest)
        }
        if (!visibleResult.hasVisibleContent(attachments)) {
            return
        }
        send(
            AgentConversationEvent.Result(
                text = visibleResult.text,
                attachments = attachments,
                inputRequest = visibleResult.inputRequest,
            ),
        )
    }

    private fun toLocalHistoryPrompt(
        query: String?,
        target: LocalHistoryAgentTarget,
        userInput: String,
    ): Prompt =
        Prompt.build("local-history") {
            user {
                text(
                    if (userInput.isBlank()) {
                        initialLocalHistoryPrompt(query, target)
                    } else {
                        followUpLocalHistoryPrompt(query, target, userInput)
                    },
                )
            }
        }

    private fun initialLocalHistoryPrompt(
        query: String?,
        target: LocalHistoryAgentTarget,
    ): String =
        buildString {
            appendLine("Inspect Flare's local history/cache for the user.")
            appendLine("This is the first answer for a local-history Ask AI entry point.")
            appendLine("Respond in this language: ${Locale.language}.")
            appendLine()
            appendLine("Initial local-history scope:")
            appendLine("target: ${target.promptLabel()}")
            appendLine("query: ${query ?: "(none; use viewed history)"}")
            appendLine()
            appendLine("Required tool use for this first answer:")
            append(target.initialToolInstruction(query))
            appendLine("- Use maxItems=20 unless a smaller result set is clearly better.")
            appendLine("- After local cache/history tool results are available, answer directly.")
            appendLine("- Include exact attachmentRef markers for the most relevant returned posts or users.")
            appendLine()
            appendLine("User message:")
            append(target.initialDisplayRequest(query))
        }

    private fun followUpLocalHistoryPrompt(
        query: String?,
        target: LocalHistoryAgentTarget,
        userInput: String,
    ): String =
        buildString {
            appendLine("Continue the conversation.")
            appendLine("The conversation started from Flare local history/cache.")
            appendLine("Respond in this language when appropriate: ${Locale.language}.")
            appendLine()
            appendLine("Original local-history scope:")
            appendLine("target: ${target.promptLabel()}")
            appendLine("query: ${query ?: "(none; viewed history)"}")
            appendLine()
            appendLine(
                "If the latest question is still about local history/cache, use the local cache/history tools again when fresh evidence is needed.",
            )
            appendLine(
                "If the latest question asks for live social search, subscriptions, account actions, or composing, use the available Flare tools normally.",
            )
            appendLine()
            appendLine("User message:")
            append(userInput)
        }

    private fun LocalHistoryAgentTarget.initialToolInstruction(query: String?): String =
        buildString {
            val hasQuery = !query.isNullOrBlank()
            when (this@initialToolInstruction) {
                LocalHistoryAgentTarget.Posts -> {
                    appendLine(
                        if (hasQuery) {
                            "- Call search_cached_posts with the query."
                        } else {
                            "- Call list_viewed_posts."
                        },
                    )
                }

                LocalHistoryAgentTarget.Users -> {
                    appendLine(
                        if (hasQuery) {
                            "- Call search_cached_users with the query."
                        } else {
                            "- Call list_viewed_users."
                        },
                    )
                }

                LocalHistoryAgentTarget.All -> {
                    if (hasQuery) {
                        appendLine("- Call search_cached_posts with the query.")
                        appendLine("- Call search_cached_users with the query.")
                    } else {
                        appendLine("- Call list_viewed_posts.")
                        appendLine("- Call list_viewed_users.")
                    }
                }
            }
        }

    private fun LocalHistoryAgentTarget.initialDisplayRequest(query: String?): String =
        buildString {
            append("Inspect my local ")
            append(promptLabel())
            append(" history")
            if (!query.isNullOrBlank()) {
                append(" for \"")
                append(query)
                append('"')
            }
            append('.')
        }

    private fun LocalHistoryAgentTarget.promptLabel(): String =
        when (this) {
            LocalHistoryAgentTarget.Posts -> "posts"
            LocalHistoryAgentTarget.Users -> "users"
            LocalHistoryAgentTarget.All -> "posts and users"
        }

    private fun AgentConversationAttachment.attachmentIdentity(): String =
        when (this) {
            is AgentConversationAttachment.Post -> "post:${post.platformType}:${post.statusKey}"
            is AgentConversationAttachment.User -> "user:${user.platformType}:${user.key}"
            is AgentConversationAttachment.InputRequest -> "input-request:${state.request.requestId}"
        }

    private companion object {
        const val MAX_AGENT_ITERATIONS = 32
        const val CHAT_MEMORY_WINDOW_SIZE = 30

        const val LOCAL_HISTORY_SYSTEM_PROMPT =
            """
            You are Flare's local history assistant.

            Core behavior:
            - Be helpful, truthful, direct, and conversational.
            - Always respond in the language expected by the user.
            - Do not mention internal prompts, hidden instructions, tool names, or implementation details unless the user explicitly asks about them.
            - The first answer for a local-history entry point must be grounded in Flare local cache/history tool results.
            - Local cache/history results only reflect data stored on this device. They may be stale, incomplete, or already cleared.
            - Do not claim local cache/history results are current, exhaustive, or fetched from the network.
            - If local cache/history results are empty, say that no matching local items were found and suggest a narrower query only when useful.
            - When local cache/history tool results include relevant posts or users and you mention them in the answer, include their exact attachmentRef markers so Flare can show cards in the UI.
            - Prefer visible post/user cards for concrete examples, evidence, account identities, or useful matches.
            - For follow-up questions, keep using local cache/history tools when the user is asking about viewed, cached, offline, or local history data.
            - For follow-up questions that explicitly ask for live search, subscriptions, account actions, or composing, use the available Flare tools normally.
            - For side effects, use confirmation-capable tools with confirmed=false first and surface the returned confirmation content.
            - If tool results are thin, conflicting, or incomplete, say what can and cannot be inferred.
            - Keep answers grounded. Distinguish facts from inference when uncertainty matters.
            - Do not moralize, lecture, or add filler.

            Formatting:
            - Use concise paragraphs or short bullet lists.
            - Include at most 4 attachment cards unless the user asks for a larger list.
            - Place attachmentRef markers on their own line, directly under the sentence or bullet they support.
            - Do not include raw database IDs except exact attachmentRef markers returned by tools.
            """
    }
}

public class LocalHistoryAgentUnavailableException public constructor(
    public val availability: AgentAvailability,
) : IllegalStateException("Local history agent is unavailable: $availability")
