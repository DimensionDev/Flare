package dev.dimension.flare.feature.agent.common

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import androidx.compose.runtime.Immutable
import dev.dimension.flare.feature.agent.database.AgentDatabase
import dev.dimension.flare.feature.agent.database.connect
import dev.dimension.flare.feature.agent.database.model.DbAgentConversation
import dev.dimension.flare.feature.agent.database.model.DbAgentMessage
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.feature.agent.presenter.buildAgentMessageParts
import dev.dimension.flare.feature.agent.presenter.markAgentInputRequestSelected
import dev.dimension.flare.feature.agent.presenter.toAgentTextParts
import dev.dimension.flare.ui.model.UiProfile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single
internal class AgentChatHistoryProvider(
    private val database: AgentDatabase,
    private val titleGenerator: AgentConversationTitleGenerator,
) : ChatHistoryProvider {
    fun observeRooms(): Flow<List<AgentChatRoom>> =
        database
            .conversationDao()
            .observeConversations()
            .map { conversations ->
                conversations.map { conversation ->
                    conversation.toChatRoom()
                }
            }

    fun observeRoom(conversationId: String): Flow<AgentChatRoom?> =
        database
            .conversationDao()
            .observeConversation(conversationId)
            .map { conversation ->
                conversation?.toChatRoom()
            }

    fun observeMessages(conversationId: String): Flow<List<AgentChatHistoryMessage>> =
        database
            .conversationDao()
            .observeMessages(conversationId)
            .map { messages ->
                messages.mapNotNull { message ->
                    message.toHistoryMessage()
                }
            }

    suspend fun hasAssistantMessage(conversationId: String): Boolean =
        database
            .conversationDao()
            .getMessages(conversationId)
            .any { message ->
                message.role == Message.Role.Assistant.name && message.toHistoryMessage() != null
            }

    suspend fun ensureConversationTitle(
        conversationId: String,
        title: String,
    ) {
        val fallbackTitle = title.agentFallbackTitle()
        val existing = database.conversationDao().getConversation(conversationId)
        val resolvedTitle = existing.agentTitleOrFallback(conversationId, fallbackTitle)
        if (existing != null && existing.title == resolvedTitle) {
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()
        database.connect {
            database.conversationDao().upsertConversation(
                DbAgentConversation(
                    conversationId = conversationId,
                    title = resolvedTitle,
                    titleGenerated = existing?.titleGenerated ?: false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = existing?.updatedAt ?: now,
                    isRunning = existing?.isRunning ?: false,
                    errorMessage = existing?.errorMessage,
                ),
            )
        }
    }

    suspend fun storeUserUiMessage(
        conversationId: String,
        text: String,
    ) {
        val displayText = text.trim()
        if (displayText.isBlank()) {
            return
        }
        storeUserUiContent(
            conversationId = conversationId,
            rawText = displayText,
            displayTitle = displayText,
            parts = displayText.toAgentTextParts(),
        )
    }

    suspend fun storeUserUiMessage(
        conversationId: String,
        displayText: String,
        parts: List<AgentMessagePart>,
    ) {
        val normalizedText = displayText.agentFallbackTitle()
        if (normalizedText.isBlank() || parts.isEmpty()) {
            return
        }
        storeUserUiContent(
            conversationId = conversationId,
            rawText = normalizedText,
            displayTitle = normalizedText,
            parts = parts,
        )
    }

    suspend fun storeUserUiInputRequestOption(
        conversationId: String,
        option: AgentInputRequest.Option,
    ) {
        val rawText = option.value.trim()
        if (rawText.isBlank()) {
            return
        }
        val parts =
            when {
                option.userPreview != null -> listOf(AgentMessagePart.UserCard(option.userPreview))
                option.postPreview != null -> listOf(AgentMessagePart.PostCard(option.postPreview))
                option.label.isNotBlank() -> option.label.toAgentTextParts()
                else -> emptyList()
            }
        if (parts.isEmpty()) {
            return
        }
        storeUserUiContent(
            conversationId = conversationId,
            rawText = rawText,
            displayTitle = option.displayTitle(),
            parts = parts,
        )
    }

    private suspend fun storeUserUiContent(
        conversationId: String,
        rawText: String,
        displayTitle: String,
        parts: List<AgentMessagePart>,
    ) {
        if (rawText.isBlank() || parts.isEmpty()) {
            return
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = database.conversationDao().getConversation(conversationId)
        val title = displayTitle.trim().takeIf { it.isNotBlank() } ?: rawText
        val nextPosition =
            database
                .conversationDao()
                .getMessages(conversationId)
                .maxOfOrNull { it.position }
                ?.plus(1)
                ?: 0
        database.connect {
            database.conversationDao().upsertConversation(
                DbAgentConversation(
                    conversationId = conversationId,
                    title = existing?.title ?: title.agentFallbackTitle(),
                    titleGenerated = existing?.titleGenerated ?: false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    isRunning = existing?.isRunning ?: false,
                    errorMessage = existing?.errorMessage,
                ),
            )
            database.conversationDao().insertMessages(
                listOf(
                    DbAgentMessage(
                        conversationId = conversationId,
                        position = nextPosition,
                        role = Message.Role.User.name,
                        text = rawText,
                        contentJson = encodeContent(parts),
                        messageJson = "",
                        createdAt = now,
                    ),
                ),
            )
        }
    }

    suspend fun storeAssistantUiContent(
        conversationId: String,
        text: String,
        supportingParts: List<AgentMessagePart>,
        inputRequest: AgentInputRequest?,
    ): ImmutableList<AgentMessagePart> {
        val parts =
            buildAgentMessageParts(
                text = text,
                supportingParts = supportingParts,
                inputRequest = inputRequest,
            )
        val message =
            database.conversationDao().getLatestMessageByRole(
                conversationId = conversationId,
                role = Message.Role.Assistant.name,
            ) ?: return parts
        database.connect {
            database.conversationDao().insertMessages(
                listOf(
                    message.copy(
                        text = text,
                        contentJson = encodeContent(parts),
                    ),
                ),
            )
        }
        return parts
    }

    suspend fun updateRoomState(
        conversationId: String,
        errorMessage: String? = null,
        isRunning: Boolean? = null,
        updateErrorMessage: Boolean = true,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = database.conversationDao().getConversation(conversationId)
        val latestMessageAt = database.conversationDao().getLatestVisibleMessageCreatedAt(conversationId)
        val nextIsRunning = isRunning ?: existing?.isRunning ?: false
        val nextErrorMessage =
            if (updateErrorMessage) {
                errorMessage
            } else {
                existing?.errorMessage
            }
        val isEmptyIdleState =
            !nextIsRunning && nextErrorMessage == null
        if (existing == null &&
            isEmptyIdleState &&
            database.conversationDao().getMessages(conversationId).isEmpty()
        ) {
            return
        }
        database.connect {
            database.conversationDao().upsertConversation(
                DbAgentConversation(
                    conversationId = conversationId,
                    title = existing?.title,
                    titleGenerated = existing?.titleGenerated ?: false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = latestMessageAt ?: existing?.updatedAt ?: now,
                    isRunning = nextIsRunning,
                    errorMessage = nextErrorMessage,
                ),
            )
        }
    }

    suspend fun markInputRequestSelected(
        conversationId: String,
        requestId: String,
        optionId: String,
    ) {
        val messages = database.conversationDao().getMessages(conversationId)
        val updated =
            messages.mapNotNull { message ->
                val content = message.decodeContent()
                var changed = false
                val updatedContent =
                    if (content.any { part ->
                            part is AgentMessagePart.Actions &&
                                part.request.requestId == requestId &&
                                !part.selected
                        }
                    ) {
                        changed = true
                        content.markAgentInputRequestSelected(
                            requestId = requestId,
                            optionId = optionId,
                        )
                    } else {
                        content
                    }
                if (!changed) {
                    null
                } else {
                    message.copy(
                        contentJson = encodeContent(updatedContent),
                    )
                }
            }
        if (updated.isEmpty()) {
            return
        }
        database.connect {
            database.conversationDao().insertMessages(updated)
        }
    }

    suspend fun clear(conversationId: String) {
        val existing = database.conversationDao().getConversation(conversationId)
        database.connect {
            database.conversationDao().deleteMessages(conversationId)
            // Initial insight runs clear old messages after the room is marked running.
            // Keep that row so history can continue observing its progress state.
            if (existing?.isRunning == true) {
                database.conversationDao().upsertConversation(existing)
            } else {
                database.conversationDao().deleteConversation(conversationId)
            }
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        database.connect {
            database.conversationDao().deleteMessages(conversationId)
            database.conversationDao().deleteConversation(conversationId)
        }
    }

    override suspend fun store(
        conversationId: String,
        messages: List<Message>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val latestMessageAt = messages.latestVisibleMessageCreatedAt() ?: now
        val existing = database.conversationDao().getConversation(conversationId)
        val existingUiContent =
            MessageUiContentIndex(
                database.conversationDao().getMessagesChronological(conversationId),
            )
        val historyMessages = messages.mapNotNull { it.toHistoryMessage(conversationId) }
        val fallbackTitle =
            existing.agentTitleOrFallback(
                conversationId = conversationId,
                fallbackTitle =
                    agentConversationFallbackTitle(
                        conversationId = conversationId,
                        messages = messages,
                        historyMessages = historyMessages,
                    ),
            )
        database.connect {
            database.conversationDao().upsertConversation(
                DbAgentConversation(
                    conversationId = conversationId,
                    title = fallbackTitle,
                    titleGenerated = existing?.titleGenerated ?: false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = latestMessageAt,
                    isRunning = existing?.isRunning ?: false,
                    errorMessage = existing?.errorMessage,
                ),
            )
            database.conversationDao().deleteMessages(conversationId)
            database.conversationDao().insertMessages(
                messages.mapIndexed { index, message ->
                    message.toDbMessage(
                        conversationId = conversationId,
                        position = index,
                        existingUiContent = existingUiContent,
                    )
                },
            )
        }
    }

    override suspend fun load(conversationId: String): List<Message> =
        database
            .conversationDao()
            .getMessagesChronological(conversationId)
            .mapNotNull { it.toMessage() }
            .sanitizeForReplay()

    suspend fun generateTitleIfNeeded(conversationId: String) {
        val existing = database.conversationDao().getConversation(conversationId) ?: return
        if (existing.titleGenerated) {
            return
        }
        val dbMessages =
            database
                .conversationDao()
                .getMessagesChronological(conversationId)
        val historyMessages = dbMessages.mapNotNull { it.toHistoryMessage() }
        if (historyMessages.none { it.role == AgentChatHistoryMessage.Role.Assistant }) {
            return
        }
        val generatedTitle =
            titleGenerator.generate(historyMessages)
                ?: run {
                    updateFallbackTitleIfNeeded(
                        conversationId = conversationId,
                        fallbackTitle =
                            agentConversationFallbackTitle(
                                conversationId = conversationId,
                                messages = dbMessages.mapNotNull { it.toMessage() },
                                historyMessages = historyMessages,
                            ),
                    )
                    return
                }
        if (database.conversationDao().getConversation(conversationId)?.titleGenerated == true) {
            return
        }
        database.conversationDao().updateGeneratedTitle(
            conversationId = conversationId,
            title = generatedTitle,
        )
    }

    private suspend fun updateFallbackTitleIfNeeded(
        conversationId: String,
        fallbackTitle: String,
    ) {
        val existing = database.conversationDao().getConversation(conversationId) ?: return
        val title = existing.agentTitleOrFallback(conversationId, fallbackTitle)
        if (title == existing.title) {
            return
        }
        database.conversationDao().updateFallbackTitle(
            conversationId = conversationId,
            title = title,
        )
    }

    private fun Message.toDbMessage(
        conversationId: String,
        position: Int,
        existingUiContent: MessageUiContentIndex,
    ): DbAgentMessage {
        val text = displayText(conversationId)
        val roleName = role.name
        return DbAgentMessage(
            conversationId = conversationId,
            position = position,
            role = roleName,
            text = text,
            contentJson =
                existingUiContent.take(roleName, text)
                    ?: encodeContent(text.toAgentTextParts()),
            messageJson = json.encodeToString<Message>(this),
            createdAt = metaInfo.timestamp.toEpochMilliseconds(),
        )
    }

    private fun List<Message>.latestVisibleMessageCreatedAt(): Long? =
        filterNot { it is Message.System }
            .maxOfOrNull { it.metaInfo.timestamp.toEpochMilliseconds() }

    private fun DbAgentMessage.toMessage(): Message? =
        runCatching {
            json.decodeFromString<Message>(messageJson)
        }.getOrNull()

    private fun List<Message>.sanitizeForReplay(): List<Message> =
        mapNotNull { message ->
            when (message) {
                is Message.System -> {
                    message
                }

                is Message.User -> {
                    val parts = message.parts.filterNot { it is MessagePart.Tool.Result }
                    parts.takeIf { it.isNotEmpty() }?.let { message.copy(parts = it) }
                }

                is Message.Assistant -> {
                    val parts =
                        message.parts.filterNot { part ->
                            part is MessagePart.Reasoning || part is MessagePart.Tool.Call
                        }
                    parts.takeIf { it.isNotEmpty() }?.let { message.copy(parts = it) }
                }
            }
        }

    private fun DbAgentConversation.toChatRoom(): AgentChatRoom =
        AgentChatRoom(
            id = conversationId,
            title = title.orEmpty().ifBlank { conversationId },
            createdAt = createdAt,
            updatedAt = updatedAt,
            isRunning = isRunning,
            currentTrace = null,
            errorMessage = errorMessage,
        )

    private fun Message.toHistoryMessage(conversationId: String? = null): AgentChatHistoryMessage? {
        val text = displayText(conversationId)
        if (text.isBlank()) {
            return null
        }
        val createdAt = metaInfo.timestamp.toEpochMilliseconds()
        val role =
            when (this) {
                is Message.System -> AgentChatHistoryMessage.Role.System
                is Message.User -> AgentChatHistoryMessage.Role.User
                is Message.Assistant -> AgentChatHistoryMessage.Role.Assistant
            }
        return AgentChatHistoryMessage(
            id = "${conversationId.orEmpty()}:${role.name}:$createdAt:${text.hashCode()}",
            role = role,
            parts = text.toAgentTextParts(),
            createdAt = createdAt,
        )
    }

    private fun DbAgentMessage.toHistoryMessage(): AgentChatHistoryMessage? {
        val content = decodeContent()
        if (content.isEmpty()) {
            return null
        }
        return AgentChatHistoryMessage(
            id = "$conversationId:$position",
            role =
                when (role) {
                    Message.Role.System.name -> return null
                    Message.Role.User.name -> AgentChatHistoryMessage.Role.User
                    Message.Role.Assistant.name -> AgentChatHistoryMessage.Role.Assistant
                    else -> return null
                },
            parts = content,
            createdAt = createdAt,
        )
    }

    private fun Message.displayText(conversationId: String? = null): String =
        textContent()
            .trim()
            .substringAfter("User message:\n")
            .trim()
            .let { text ->
                when (this) {
                    is Message.Assistant -> text.removeAgentInputRequestUiMetadata().cleanAgentVisibleText()
                    is Message.User -> text.cleanAgentUserVisibleText(conversationId)
                    is Message.System -> text
                }
            }

    private fun String.cleanAgentUserVisibleText(conversationId: String?): String {
        extractLatestInsightQuestion()?.let { return it }
        return when {
            conversationId?.startsWith(STATUS_INSIGHT_CONVERSATION_PREFIX) == true && isStatusInsightSourcePrompt() -> {
                agentInsightSourceFallbackTitle(conversationId)?.agentFallbackTitle().orEmpty()
            }

            conversationId?.startsWith(PROFILE_INSIGHT_CONVERSATION_PREFIX) == true && isProfileInsightSourcePrompt() -> {
                agentInsightSourceFallbackTitle(conversationId)?.agentFallbackTitle().orEmpty()
            }

            else -> {
                this
            }
        }
    }

    private fun String.extractLatestInsightQuestion(): String? {
        val latestQuestion =
            substringAfter(LATEST_USER_QUESTION_MARKER, missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
                ?: return null
        return listOf(
            CURRENT_POST_SNAPSHOT_MARKER,
            CURRENT_PROFILE_SNAPSHOT_MARKER,
        ).fold(latestQuestion) { text, delimiter ->
            text.substringBefore(delimiter)
        }.trim()
            .takeIf { it.isNotBlank() }
    }

    private fun String.isStatusInsightSourcePrompt(): Boolean =
        contains("Analyze this social post for the user.") ||
            contains("Current post snapshot:") ||
            contains("\nPost:\nplatform:")

    private fun String.isProfileInsightSourcePrompt(): Boolean =
        contains("Analyze this social profile for the user.") ||
            contains("Current profile snapshot:") ||
            contains("\nProfile:\nplatform:")

    private fun AgentInputRequest.Option.displayTitle(): String =
        when {
            userPreview != null -> {
                val name = userPreview.name.raw.trim()
                val handle = userPreview.handle.raw.trim()
                name.ifBlank { handle }
            }

            postPreview != null -> {
                postPreview.content.original.raw
                    .trim()
            }

            label.isNotBlank() -> {
                label.trim()
            }

            else -> {
                value.trim()
            }
        }

    private companion object {
        const val STATUS_INSIGHT_CONVERSATION_PREFIX = "status-insight:"
        const val PROFILE_INSIGHT_CONVERSATION_PREFIX = "profile-insight:"
        const val LATEST_USER_QUESTION_MARKER = "Latest user question:\n"
        const val CURRENT_POST_SNAPSHOT_MARKER = "\n\nCurrent post snapshot:"
        const val CURRENT_PROFILE_SNAPSHOT_MARKER = "\n\nCurrent profile snapshot:"

        val json =
            Json {
                ignoreUnknownKeys = true
            }
    }

    private fun DbAgentMessage.decodeContent(): ImmutableList<AgentMessagePart> =
        runCatching {
            json.decodeFromString<List<AgentMessagePart>>(contentJson).toImmutableList()
        }.getOrElse {
            text.toAgentTextParts()
        }

    private fun encodeContent(content: List<AgentMessagePart>): String = json.encodeToString<List<AgentMessagePart>>(content)

    private data class MessageUiContentKey(
        val role: String,
        val text: String,
    )

    private class MessageUiContentIndex(
        messages: List<DbAgentMessage>,
    ) {
        private val contentByKey: Map<MessageUiContentKey, List<String>> =
            messages.groupBy(
                keySelector = { message -> MessageUiContentKey(message.role, message.text) },
                valueTransform = { message -> message.contentJson },
            )
        private val nextIndexByKey = mutableMapOf<MessageUiContentKey, Int>()

        fun take(
            role: String,
            text: String,
        ): String? {
            val key = MessageUiContentKey(role, text)
            val index = nextIndexByKey[key] ?: 0
            nextIndexByKey[key] = index + 1
            return contentByKey[key]?.getOrNull(index)
        }
    }
}

@Immutable
public data class AgentChatRoom(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isRunning: Boolean,
    val currentTrace: AgentTrace?,
    val errorMessage: String?,
) {
    public companion object {
        public fun empty(id: String): AgentChatRoom =
            AgentChatRoom(
                id = id,
                title = "",
                createdAt = 0L,
                updatedAt = 0L,
                isRunning = false,
                currentTrace = null,
                errorMessage = null,
            )
    }
}

public data class AgentChatHistoryMessage(
    val id: String,
    val role: Role,
    val parts: ImmutableList<AgentMessagePart>,
    val createdAt: Long,
) {
    public val isUser: Boolean
        get() = role == Role.User

    public val isAssistant: Boolean
        get() = role == Role.Assistant

    public enum class Role {
        System,
        User,
        Assistant,
    }
}
