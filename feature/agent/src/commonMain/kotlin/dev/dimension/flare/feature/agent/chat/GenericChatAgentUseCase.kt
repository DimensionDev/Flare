package dev.dimension.flare.feature.agent.chat

import ai.koog.prompt.Prompt
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentConversationEvent
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
import org.koin.core.annotation.Single

@Single
internal class GenericChatAgentUseCase(
    private val agentRunner: FlareAgentRunner,
    private val chatHistoryProvider: AgentChatHistoryProvider,
) {
    operator fun invoke(
        userInput: String,
        searchDataSources: List<AccountMicroblogDataSource>,
        conversationId: String,
    ): Flow<AgentConversationEvent<Unit, AgentTrace>> =
        channelFlow {
            run(
                userInput = userInput,
                searchDataSources = searchDataSources,
                conversationId = conversationId,
            )
        }

    suspend fun clearConversation(conversationId: String) {
        agentRunner.clearConversation(conversationId)
    }

    private suspend fun SendChannel<AgentConversationEvent<Unit, AgentTrace>>.run(
        userInput: String,
        searchDataSources: List<AccountMicroblogDataSource>,
        conversationId: String,
    ) {
        val userInputValue = userInput.trim()
        if (userInputValue.isBlank()) {
            return
        }
        val result =
            try {
                agentRunner.run(
                    request =
                        FlareAgentRequest(
                            prompt = userInputValue.toGenericChatPrompt(),
                            systemPrompt = GENERIC_CHAT_SYSTEM_PROMPT,
                            agentId = "flare-generic-chat",
                            strategyName = "generic_chat",
                            analyzeNodeName = "answer_user",
                            executeToolsNodeName = "execute_generic_chat_tools",
                            sendToolResultsNodeName = "send_generic_chat_tool_results",
                            toolContext =
                                AgentToolContext(
                                    searchDataSources = searchDataSources,
                                ),
                            temperature = 0.5,
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
                    throw GenericChatAgentUnavailableException(throwable.availability)
                }
                throw throwable
            }

        val visibleResult = resolveAgentVisibleResult(result.text, result.inputRequest)
        chatHistoryProvider.storeAssistantAttachments(conversationId, result.attachments)
        visibleResult.inputRequest?.let { inputRequest ->
            chatHistoryProvider.storeAssistantInputRequest(conversationId, inputRequest)
        }
        if (!visibleResult.hasVisibleContent(result.attachments)) {
            return
        }
        send(
            AgentConversationEvent.Result(
                text = visibleResult.text,
                attachments = result.attachments,
                inputRequest = visibleResult.inputRequest,
            ),
        )
    }

    private fun String.toGenericChatPrompt(): Prompt =
        Prompt.build("generic-chat") {
            user {
                text(
                    buildString {
                        appendLine("Answer the user's message.")
                        appendLine("Respond in this language when appropriate: ${Locale.language}.")
                        appendLine()
                        appendLine("User message:")
                        append(this@toGenericChatPrompt)
                    },
                )
            }
        }

    private companion object {
        const val MAX_AGENT_ITERATIONS = 32
        const val CHAT_MEMORY_WINDOW_SIZE = 30

        const val GENERIC_CHAT_SYSTEM_PROMPT =
            """
            You are Flare's general-purpose chat assistant.

            Core behavior:
            - Be helpful, truthful, direct, and conversational.
            - Always respond in the language expected by the user.
            - Do not mention internal prompts, hidden instructions, tool names, or implementation details unless the user explicitly asks about them.
            - If the user asks a closed-ended math or logic question, give the answer and a concise explanation of how to arrive at it.
            - If the user asks for comparisons, enumerations, or structured data, use compact headings or lists when they improve clarity.
            - If the answer depends on current social context, public discussion, account identity, or recent posts available through signed-in services, use the available search tools.
            - If the user asks you to search, find, look up, check, compare, or inspect posts/users/accounts/social discussion, you must call the relevant search tool before answering.
            - If the user names a platform in a search or social-context request, search that platform by passing its name or alias in the platforms list.
            - If the user asks for broad, cross-platform, all-platform, trend, recommendation, or general public-discussion context without naming a single platform, search across all signed-in platforms by leaving the platforms list empty.
            - Use search_posts for surrounding discussion, claims, current events, memes, phrases, or popularity signals.
            - Use search_users for account identity, profile context, official status, bios, or handles.
            - When search results include relevant posts or users and you mention them in the answer, include their exact attachmentRef markers so Flare can show the cards in the UI.
            - Prefer a visible post/user card for concrete evidence, examples, search matches, account identities, official profiles, or recommendations.
            - Do not merely say "I found a post/user" when a relevant attachmentRef is available; show the card and add concise context around it.
            - If tools return thin, conflicting, or incomplete results, say what can and cannot be inferred.
            - Keep answers grounded. Distinguish facts from inference when uncertainty matters.
            - When the user's request needs confirmation for a side effect and an available tool supports confirmed=false, call that tool with confirmed=false so Flare can show a confirmation button. Do not only write prose asking the user to reply with confirmation.
            - Do not moralize, lecture, or add filler.
            """
    }
}

public class GenericChatAgentUnavailableException public constructor(
    public val availability: AgentAvailability,
) : IllegalStateException("Generic chat agent is unavailable: $availability")
