package dev.dimension.flare.feature.agent.common

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import androidx.compose.runtime.Immutable
import dev.dimension.flare.feature.agent.database.AgentDatabase
import dev.dimension.flare.feature.agent.database.connect
import dev.dimension.flare.feature.agent.database.model.DbAgentContext
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single
internal fun provideAgentChatHistoryProvider(
    database: AgentDatabase,
    titleGenerator: AgentConversationTitleGenerator,
): AgentChatHistoryProvider = AgentChatHistoryProvider(database, titleGenerator::generate)

internal class AgentChatHistoryProvider(
    private val database: AgentDatabase,
    private val generateTitle: suspend (List<AgentChatHistoryMessage>) -> String?,
) : ChatHistoryProvider {
    private val confirmations = MutableStateFlow<Map<String, String>>(emptyMap())

    fun consumeConfirmation(conversationId: String): String? = confirmations.getAndUpdate { it - conversationId }[conversationId]

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

    suspend fun consumeRetry(conversationId: String): AgentFailure? {
        val failure =
            database
                .conversationDao()
                .getConversation(conversationId)
                ?.errorMessage
                ?.let(AgentFailure::decode)
        if (failure?.canRetry != true) return null
        // A crash during resumed tools must not expose the previous safe boundary again.
        updateRoomState(conversationId, errorMessage = AgentFailure(AgentFailureKind.Interrupted, "").encode())
        return failure
    }

    suspend fun markInputRequestSelected(
        conversationId: String,
        requestId: String,
        optionId: String,
    ) {
        val confirmation =
            database.connect {
                val messages = database.conversationDao().getMessages(conversationId)
                val option =
                    messages
                        .asSequence()
                        .flatMap { it.decodeContent().asSequence() }
                        .filterIsInstance<AgentMessagePart.Actions>()
                        .filter { it.request.requestId == requestId && !it.selected }
                        .flatMap { it.request.options.asSequence() }
                        .firstOrNull { it.id == optionId } ?: return@connect null
                val updated =
                    messages.mapNotNull { message ->
                        val content = message.decodeContent()
                        val selected = content.markAgentInputRequestSelected(requestId, optionId)
                        if (content == selected) null else message.copy(contentJson = encodeContent(selected))
                    }
                database.conversationDao().insertMessages(updated)
                option.confirmationKey
            }
        confirmations.update { if (confirmation == null) it - conversationId else it + (conversationId to confirmation) }
    }

    suspend fun clear(conversationId: String) {
        confirmations.update { it - conversationId }
        val existing = database.conversationDao().getConversation(conversationId)
        database.connect {
            database.conversationDao().deleteContext(conversationId)
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
        confirmations.update { it - conversationId }
        database.connect {
            database.conversationDao().deleteContext(conversationId)
            database.conversationDao().deleteMessages(conversationId)
            database.conversationDao().deleteConversation(conversationId)
        }
    }

    override suspend fun store(
        conversationId: String,
        messages: List<Message>,
    ) = storeMessages(conversationId, messages)

    suspend fun storeCheckpoint(
        conversationId: String,
        messages: List<Message>,
        checkpoint: AgentContextCheckpoint,
    ) = storeMessages(conversationId, messages, checkpoint = checkpoint)

    suspend fun storeAssistantResponse(
        conversationId: String,
        messages: List<Message>,
        supportingParts: List<AgentMessagePart>,
        inputRequest: AgentPendingInputRequest?,
    ) {
        val response = messages.last() as Message.Assistant
        val visibleResult =
            resolveAgentVisibleResult(
                response.textContent(),
                inputRequest.takeIf { response.parts.none { it is MessagePart.Tool.Call } },
            )
        storeMessages(
            conversationId,
            messages,
            lastResponseParts = buildAgentMessageParts(visibleResult.text, supportingParts, visibleResult.inputRequest),
        )
    }

    private suspend fun storeMessages(
        conversationId: String,
        history: List<Message>,
        lastResponseParts: List<AgentMessagePart>? = null,
        checkpoint: AgentContextCheckpoint? = null,
    ) {
        val messages = history.filterNot { it.id == AGENT_CONTEXT_SUMMARY_ID }
        val now = Clock.System.now().toEpochMilliseconds()
        val latestMessageAt = messages.latestVisibleMessageCreatedAt() ?: now
        val existing = database.conversationDao().getConversation(conversationId)
        val existingMessages = database.conversationDao().getMessagesChronological(conversationId)
        val existingById = existingMessages.associateBy { it.toMessage()?.id ?: it.messageId() }
        val pendingUiMessages =
            existingMessages
                .filter { it.messageJson.isEmpty() }
                .groupBy { MessageUiContentKey(it.role, it.text) }
                .mapValues { (_, messages) -> ArrayDeque(messages) }
        var nextPosition = (existingMessages.maxOfOrNull { it.position } ?: -1) + 1
        // Keep optimistic user positions while placing new system messages before them.
        var nextSystemPosition = (existingMessages.minOfOrNull { it.position } ?: 0) - messages.count { it is Message.System }
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
            database.conversationDao().insertMessages(
                messages.mapIndexed { index, message ->
                    val previous =
                        existingById[message.id]
                            ?: pendingUiMessages[
                                MessageUiContentKey(
                                    message.role.name,
                                    message.displayText(conversationId),
                                ),
                            ]?.removeFirstOrNull()
                    message.toDbMessage(
                        conversationId = conversationId,
                        position = previous?.position ?: if (message is Message.System) nextSystemPosition++ else nextPosition++,
                        existingUiContent =
                            if (index == messages.lastIndex && lastResponseParts != null) {
                                encodeContent(lastResponseParts)
                            } else {
                                previous?.contentJson
                            },
                    )
                },
            )
            if (checkpoint != null) {
                val throughPosition =
                    database
                        .conversationDao()
                        .getMessagesChronological(conversationId)
                        .first { it.toMessage()?.id == checkpoint.throughMessageId }
                        .position
                val previousPosition = database.conversationDao().getContext(conversationId)?.throughPosition ?: throughPosition
                database.conversationDao().upsertContext(
                    DbAgentContext(conversationId, checkpoint.summary, maxOf(previousPosition, throughPosition)),
                )
            }
        }
    }

    override suspend fun load(conversationId: String): List<Message> {
        val checkpoint = database.conversationDao().getContext(conversationId)
        val history = database.conversationDao().getMessagesChronological(conversationId)
        // Compaction pins the current request even when it precedes the checkpoint.
        val currentRequestPosition =
            if (checkpoint != null) {
                history
                    .lastOrNull { stored ->
                        stored.role == Message.Role.User.name &&
                            stored.toMessage()?.parts?.any { it !is MessagePart.Tool.Result } == true
                    }?.position
            } else {
                null
            }
        val messages =
            history
                .filter { checkpoint == null || it.position > checkpoint.throughPosition || it.position == currentRequestPosition }
                .mapNotNull { stored ->
                    stored.toMessage()?.let { message ->
                        if (message.id == null) message.withAgentMessageId(stored.messageId()) else message
                    }
                }.sanitizeForReplay()
        return listOfNotNull(checkpoint?.let { agentContextSummary(it.summary) }) + messages
    }

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
            generateTitle(historyMessages)
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
        existingUiContent: String?,
    ): DbAgentMessage {
        val text = displayText(conversationId)
        val roleName = role.name
        return DbAgentMessage(
            conversationId = conversationId,
            position = position,
            role = roleName,
            text = text,
            contentJson =
                existingUiContent
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

    private fun DbAgentMessage.messageId(): String = "$conversationId:$position"

    private fun List<Message>.sanitizeForReplay(): List<Message> {
        val pending = mutableMapOf<Pair<String?, String>, Pair<Int, Int>>()
        val completed = mutableSetOf<Pair<Int, Int>>()
        forEachIndexed { messageIndex, message ->
            if (message.parts.none { it is MessagePart.Tool.Result }) pending.clear()
            message.parts.forEachIndexed { partIndex, part ->
                val position = messageIndex to partIndex
                when (part) {
                    is MessagePart.Tool.Call -> {
                        pending[part.id to part.tool] = position
                    }

                    is MessagePart.Tool.Result -> {
                        pending.remove(part.id to part.tool)?.let { call ->
                            completed += call
                            completed += position
                        }
                    }

                    else -> {}
                }
            }
        }
        return mapIndexedNotNull { messageIndex, message ->
            when (message) {
                is Message.System -> {
                    message
                }

                is Message.User -> {
                    val parts =
                        message.parts.filterIndexed { partIndex, part ->
                            part !is MessagePart.Tool.Result || (messageIndex to partIndex) in completed
                        }
                    parts.takeIf { it.isNotEmpty() }?.let { message.copy(parts = it) }
                }

                is Message.Assistant -> {
                    val parts =
                        message.parts.filterIndexed { partIndex, part ->
                            part !is MessagePart.Reasoning && (part !is MessagePart.Tool.Call || (messageIndex to partIndex) in completed)
                        }
                    parts.takeIf { it.isNotEmpty() }?.let { message.copy(parts = it) }
                }
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
}

internal fun Message.withAgentMessageId(id: String): Message =
    when (this) {
        is Message.System -> copy(id = id)
        is Message.User -> copy(id = id)
        is Message.Assistant -> copy(id = id)
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
    public val error: AgentFailure? get() = errorMessage?.let(AgentFailure::decode)

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
