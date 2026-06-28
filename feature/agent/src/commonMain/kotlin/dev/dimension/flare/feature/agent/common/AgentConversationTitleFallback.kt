package dev.dimension.flare.feature.agent.common

import ai.koog.prompt.message.Message
import dev.dimension.flare.feature.agent.database.model.DbAgentConversation
import dev.dimension.flare.feature.agent.presenter.agentMessageText

internal fun agentConversationFallbackTitle(
    conversationId: String,
    messages: List<Message>,
    historyMessages: List<AgentChatHistoryMessage>,
): String =
    historyMessages.firstVisibleTitle(AgentChatHistoryMessage.Role.User)
        ?: messages.firstNotNullOfOrNull { message ->
            (message as? Message.User)
                ?.rawPromptText()
                ?.agentInsightSourceFallbackTitle(conversationId)
                ?.agentFallbackTitle()
        }
        ?: historyMessages.firstVisibleTitle(AgentChatHistoryMessage.Role.Assistant)
        ?: conversationId

internal fun DbAgentConversation?.agentTitleOrFallback(
    conversationId: String,
    fallbackTitle: String,
): String {
    val currentTitle = this?.title.orEmpty()
    return when {
        this == null -> fallbackTitle
        titleGenerated -> currentTitle
        shouldUseAgentFallbackTitle(conversationId) -> fallbackTitle
        else -> currentTitle
    }
}

internal fun DbAgentConversation.shouldUseAgentFallbackTitle(conversationId: String): Boolean {
    val currentTitle = title.orEmpty()
    return !titleGenerated &&
        (
            currentTitle.isBlank() ||
                currentTitle == conversationId ||
                currentTitle.isTechnicalAgentTitle()
        )
}

internal fun String.agentFallbackTitle(): String =
    lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(MAX_FALLBACK_TITLE_CHARS)
        .orEmpty()
        .ifBlank { "Flare AI" }

internal fun String.agentInsightSourceFallbackTitle(conversationId: String): String? =
    when {
        conversationId.startsWith(STATUS_INSIGHT_CONVERSATION_PREFIX) && isStatusInsightSourcePrompt() -> {
            promptField("content")
                ?: promptField("contentWarning")
                ?: promptField("cardTitle")
                ?: promptField("authorName")
        }

        conversationId.startsWith(PROFILE_INSIGHT_CONVERSATION_PREFIX) && isProfileInsightSourcePrompt() -> {
            promptField("displayName")
                ?: promptField("handle")
                ?: promptField("description")
        }

        else -> {
            null
        }
    }?.takeIf { it.isNotBlank() }

private fun List<AgentChatHistoryMessage>.firstVisibleTitle(role: AgentChatHistoryMessage.Role): String? =
    firstOrNull { it.role == role }
        ?.parts
        ?.agentMessageText()
        ?.takeIf { it.isNotBlank() }
        ?.agentFallbackTitle()

private fun Message.User.rawPromptText(): String =
    textContent()
        .trim()
        .substringAfter("User message:\n")
        .trim()

private fun String.promptField(name: String): String? =
    lineSequence()
        .firstOrNull { line -> line.startsWith("$name:") }
        ?.substringAfter(":")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun String.isStatusInsightSourcePrompt(): Boolean =
    contains("Analyze this social post for the user.") ||
        contains("Current post snapshot:") ||
        contains("\nPost:\nplatform:")

private fun String.isProfileInsightSourcePrompt(): Boolean =
    contains("Analyze this social profile for the user.") ||
        contains("Current profile snapshot:") ||
        contains("\nProfile:\nplatform:")

private fun String.isTechnicalAgentTitle(): Boolean =
    startsWith(GENERIC_CHAT_CONVERSATION_PREFIX) ||
        startsWith(LOCAL_HISTORY_CONVERSATION_PREFIX) ||
        startsWith(STATUS_INSIGHT_CONVERSATION_PREFIX) ||
        startsWith(PROFILE_INSIGHT_CONVERSATION_PREFIX)

private const val MAX_FALLBACK_TITLE_CHARS = 80
private const val GENERIC_CHAT_CONVERSATION_PREFIX = "generic-chat:"
private const val LOCAL_HISTORY_CONVERSATION_PREFIX = "local-history:"
private const val STATUS_INSIGHT_CONVERSATION_PREFIX = "status-insight:"
private const val PROFILE_INSIGHT_CONVERSATION_PREFIX = "profile-insight:"
