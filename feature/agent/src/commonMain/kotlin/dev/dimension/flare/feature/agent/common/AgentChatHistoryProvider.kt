package dev.dimension.flare.feature.agent.common

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.Message
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.feature.agent.database.AgentDatabase
import dev.dimension.flare.feature.agent.database.connect
import dev.dimension.flare.feature.agent.database.model.DbAgentConversation
import dev.dimension.flare.feature.agent.database.model.DbAgentConversationAttachment
import dev.dimension.flare.feature.agent.database.model.DbAgentMessage
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val titleScope = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)

    fun observeRecords(): Flow<List<AgentChatHistoryRecord>> =
        database
            .conversationDao()
            .observeConversations()
            .map { conversations ->
                conversations.map { conversation ->
                    AgentChatHistoryRecord(
                        conversationId = conversation.conversationId,
                        title = conversation.title.orEmpty().ifBlank { conversation.conversationId },
                        updatedAt = conversation.updatedAt,
                    )
                }
            }

    fun observeRecord(conversationId: String): Flow<AgentChatHistoryRecord?> =
        database
            .conversationDao()
            .observeConversation(conversationId)
            .map { conversation ->
                conversation?.toHistoryRecord()
            }

    fun observeMessages(conversationId: String): Flow<List<AgentChatHistoryMessage>> =
        database
            .conversationDao()
            .observeMessages(conversationId)
            .map { messages ->
                messages.mapNotNull { it.toHistoryMessage() }
            }

    fun observeAttachments(
        conversationId: String,
        owner: AgentConversationAttachmentOwner,
        groupKey: String,
    ): Flow<List<AgentConversationAttachment>> =
        database
            .conversationDao()
            .observeAttachments(
                conversationId = conversationId,
                owner = owner.name,
                groupKey = groupKey,
            ).map { attachments ->
                attachments.mapNotNull { it.toAttachment() }
            }

    fun observeStatusInsightPosts(conversationId: String): Flow<List<UiTimelineV2.Post>> =
        observeAttachments(
            conversationId = conversationId,
            owner = AgentConversationAttachmentOwner.Context,
            groupKey = STATUS_INSIGHT_SOURCE_GROUP_KEY,
        ).map { attachments ->
            attachments.mapNotNull { (it as? AgentConversationAttachment.Post)?.post }
        }

    suspend fun storeAttachments(
        conversationId: String,
        owner: AgentConversationAttachmentOwner,
        groupKey: String,
        attachments: List<AgentConversationAttachment>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        database.connect {
            database.conversationDao().deleteAttachmentGroup(
                conversationId = conversationId,
                owner = owner.name,
                groupKey = groupKey,
            )
            database.conversationDao().insertAttachments(
                attachments.mapIndexed { index, attachment ->
                    attachment.toDbAttachment(
                        conversationId = conversationId,
                        owner = owner,
                        groupKey = groupKey,
                        position = index,
                        createdAt = now,
                    )
                },
            )
        }
    }

    suspend fun storeStatusInsightSourcePosts(
        conversationId: String,
        posts: List<UiTimelineV2.Post>,
    ) = storeAttachments(
        conversationId = conversationId,
        owner = AgentConversationAttachmentOwner.Context,
        groupKey = STATUS_INSIGHT_SOURCE_GROUP_KEY,
        attachments = posts.map { AgentConversationAttachment.Post(it) },
    )

    suspend fun clear(conversationId: String) {
        database.connect {
            database.conversationDao().deleteMessages(conversationId)
            database.conversationDao().deleteAttachments(conversationId)
            database.conversationDao().deleteConversation(conversationId)
        }
    }

    override suspend fun store(
        conversationId: String,
        messages: List<Message>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = database.conversationDao().getConversation(conversationId)
        val historyMessages = messages.mapNotNull { it.toHistoryMessage() }
        val fallbackTitle =
            existing?.title
                ?: historyMessages.firstOrNull { it.role == AgentChatHistoryMessage.Role.User }?.text?.fallbackTitle()
                ?: conversationId
        database.connect {
            database.conversationDao().upsertConversation(
                DbAgentConversation(
                    conversationId = conversationId,
                    title = fallbackTitle,
                    titleGenerated = existing?.titleGenerated ?: false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            database.conversationDao().deleteMessages(conversationId)
            database.conversationDao().insertMessages(
                messages.mapIndexed { index, message ->
                    message.toDbMessage(
                        conversationId = conversationId,
                        position = index,
                    )
                },
            )
        }
        maybeGenerateTitle(
            conversationId = conversationId,
            messages = historyMessages,
            existing = existing,
        )
    }

    override suspend fun load(conversationId: String): List<Message> =
        database
            .conversationDao()
            .getMessages(conversationId)
            .mapNotNull { it.toMessage() }

    private fun maybeGenerateTitle(
        conversationId: String,
        messages: List<AgentChatHistoryMessage>,
        existing: DbAgentConversation?,
    ) {
        if (existing?.titleGenerated == true || messages.none { it.role == AgentChatHistoryMessage.Role.Assistant }) {
            return
        }
        titleScope.launch {
            val generatedTitle = titleGenerator.generate(messages) ?: return@launch
            database.conversationDao().updateGeneratedTitle(
                conversationId = conversationId,
                title = generatedTitle,
            )
        }
    }

    private fun Message.toDbMessage(
        conversationId: String,
        position: Int,
    ): DbAgentMessage =
        DbAgentMessage(
            conversationId = conversationId,
            position = position,
            role = role.name,
            text = displayText(),
            messageJson = json.encodeToString<Message>(this),
            createdAt = metaInfo.timestamp.toEpochMilliseconds(),
        )

    private fun DbAgentMessage.toMessage(): Message? =
        runCatching {
            json.decodeFromString<Message>(messageJson)
        }.getOrNull()

    private fun DbAgentConversation.toHistoryRecord(): AgentChatHistoryRecord =
        AgentChatHistoryRecord(
            conversationId = conversationId,
            title = title.orEmpty().ifBlank { conversationId },
            updatedAt = updatedAt,
        )

    private fun Message.toHistoryMessage(): AgentChatHistoryMessage? {
        val text = displayText()
        if (text.isBlank()) {
            return null
        }
        return AgentChatHistoryMessage(
            role =
                when (this) {
                    is Message.System -> AgentChatHistoryMessage.Role.System
                    is Message.User -> AgentChatHistoryMessage.Role.User
                    is Message.Assistant -> AgentChatHistoryMessage.Role.Assistant
                },
            text = text,
            createdAt = metaInfo.timestamp.toEpochMilliseconds(),
        )
    }

    private fun DbAgentMessage.toHistoryMessage(): AgentChatHistoryMessage? {
        if (text.isBlank()) {
            return null
        }
        return AgentChatHistoryMessage(
            role =
                when (role) {
                    Message.Role.System.name -> AgentChatHistoryMessage.Role.System
                    Message.Role.User.name -> AgentChatHistoryMessage.Role.User
                    Message.Role.Assistant.name -> AgentChatHistoryMessage.Role.Assistant
                    else -> return null
                },
            text = text,
            createdAt = createdAt,
        )
    }

    private fun AgentConversationAttachment.toDbAttachment(
        conversationId: String,
        owner: AgentConversationAttachmentOwner,
        groupKey: String,
        position: Int,
        createdAt: Long,
    ): DbAgentConversationAttachment =
        when (this) {
            is AgentConversationAttachment.Post -> {
                DbAgentConversationAttachment(
                    conversationId = conversationId,
                    owner = owner.name,
                    groupKey = groupKey,
                    position = position,
                    type = AgentConversationAttachmentType.Post.name,
                    contentJson = json.encodeToString<UiTimelineV2.Post>(post),
                    createdAt = createdAt,
                )
            }

            is AgentConversationAttachment.User -> {
                DbAgentConversationAttachment(
                    conversationId = conversationId,
                    owner = owner.name,
                    groupKey = groupKey,
                    position = position,
                    type = AgentConversationAttachmentType.User.name,
                    contentJson = json.encodeToString<UiProfile>(user),
                    createdAt = createdAt,
                )
            }
        }

    private fun DbAgentConversationAttachment.toAttachment(): AgentConversationAttachment? =
        runCatching {
            when (type) {
                AgentConversationAttachmentType.Post.name -> {
                    AgentConversationAttachment.Post(
                        post = json.decodeFromString<UiTimelineV2.Post>(contentJson),
                    )
                }

                AgentConversationAttachmentType.User.name -> {
                    AgentConversationAttachment.User(
                        user = json.decodeFromString<UiProfile>(contentJson),
                    )
                }

                else -> {
                    null
                }
            }
        }.getOrNull()

    private fun Message.displayText(): String =
        textContent()
            .trim()
            .substringAfter("User message:\n")
            .trim()

    private fun String.fallbackTitle(): String =
        lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(MAX_FALLBACK_TITLE_CHARS)
            .orEmpty()
            .ifBlank { "Flare Agent" }

    private companion object {
        const val MAX_FALLBACK_TITLE_CHARS = 80
        const val STATUS_INSIGHT_SOURCE_GROUP_KEY = "status-insight-source"
        val json =
            Json {
                ignoreUnknownKeys = true
            }
    }
}

internal enum class AgentConversationAttachmentOwner {
    Context,
    User,
    Assistant,
}

private enum class AgentConversationAttachmentType {
    Post,
    User,
}

internal sealed interface AgentConversationAttachment {
    data class Post(
        val post: UiTimelineV2.Post,
    ) : AgentConversationAttachment

    data class User(
        val user: UiProfile,
    ) : AgentConversationAttachment
}

internal data class AgentChatHistoryRecord(
    val conversationId: String,
    val title: String,
    val updatedAt: Long,
)

internal data class AgentChatHistoryMessage(
    val role: Role,
    val text: String,
    val createdAt: Long,
) {
    enum class Role {
        System,
        User,
        Assistant,
    }
}
