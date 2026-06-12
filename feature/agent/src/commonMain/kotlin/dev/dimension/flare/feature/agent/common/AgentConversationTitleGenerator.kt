package dev.dimension.flare.feature.agent.common

import ai.koog.prompt.Prompt
import dev.dimension.flare.feature.agent.presenter.agentMessageText
import dev.dimension.flare.feature.agent.runtime.FlareAgentRuntimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
internal class AgentConversationTitleGenerator(
    private val runtimeProvider: FlareAgentRuntimeProvider,
) {
    suspend fun generate(messages: List<AgentChatHistoryMessage>): String? {
        val content =
            messages
                .filter { it.role != AgentChatHistoryMessage.Role.System }
                .takeLast(MAX_MESSAGES)
                .map { message -> message.role to message.parts.agentMessageText() }
                .filter { (_, text) -> text.isNotBlank() }
                .joinToString("\n") { message ->
                    "${message.first.name}: ${message.second.take(MAX_MESSAGE_CHARS)}"
                }.takeIf { it.isNotBlank() }
                ?: return null
        val runtime = runtimeProvider.createRuntime() ?: return null
        return try {
            runtime
                .promptExecutor
                .execute(
                    prompt =
                        Prompt.build("agent-conversation-title") {
                            system {
                                text(
                                    """
                                    Generate a short title for a chat conversation.
                                    Rules:
                                    - Use the same language as the conversation when possible.
                                    - For Chinese, Japanese, or Korean, keep it around 10 characters.
                                    - For other languages, keep it under 4 words.
                                    - Return only the title.
                                    - Do not use quotation marks.
                                    """.trimIndent(),
                                )
                            }
                            user {
                                text(content)
                            }
                        },
                    model = runtime.model,
                ).textContent()
                .cleanTitle()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            }
            null
        } finally {
            withContext(NonCancellable) {
                runtime.promptExecutor.close()
            }
        }
    }

    private fun String.cleanTitle(): String? =
        trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .removePrefix("'")
            .removeSuffix("'")
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.take(MAX_TITLE_CHARS)
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val MAX_MESSAGES = 8
        const val MAX_MESSAGE_CHARS = 500
        const val MAX_TITLE_CHARS = 24
    }
}
