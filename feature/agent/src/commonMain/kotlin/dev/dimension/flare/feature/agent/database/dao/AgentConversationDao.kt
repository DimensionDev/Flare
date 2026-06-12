package dev.dimension.flare.feature.agent.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.feature.agent.database.model.DbAgentConversation
import dev.dimension.flare.feature.agent.database.model.DbAgentMessage
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AgentConversationDao {
    @Query(
        """
        SELECT
            conversation.conversationId AS conversationId,
            conversation.title AS title,
            conversation.titleGenerated AS titleGenerated,
            conversation.createdAt AS createdAt,
            COALESCE(MAX(message.createdAt), conversation.createdAt) AS updatedAt,
            conversation.isRunning AS isRunning,
            conversation.currentTraceJson AS currentTraceJson,
            conversation.traceHistoryJson AS traceHistoryJson,
            conversation.errorMessage AS errorMessage
        FROM agent_conversations AS conversation
        LEFT JOIN agent_messages AS message
            ON message.conversationId = conversation.conversationId
            AND message.role != 'System'
        GROUP BY conversation.conversationId
        ORDER BY COALESCE(MAX(message.createdAt), conversation.createdAt) DESC, conversation.createdAt DESC
        """,
    )
    fun observeConversations(): Flow<List<DbAgentConversation>>

    @Query("SELECT * FROM agent_conversations WHERE conversationId = :conversationId")
    fun observeConversation(conversationId: String): Flow<DbAgentConversation?>

    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId ORDER BY position ASC")
    fun observeMessages(conversationId: String): Flow<List<DbAgentMessage>>

    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId ORDER BY position ASC")
    suspend fun getMessages(conversationId: String): List<DbAgentMessage>

    @Query("SELECT MAX(createdAt) FROM agent_messages WHERE conversationId = :conversationId AND role != 'System'")
    suspend fun getLatestVisibleMessageCreatedAt(conversationId: String): Long?

    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId AND role = :role ORDER BY position DESC LIMIT 1")
    suspend fun getLatestMessageByRole(
        conversationId: String,
        role: String,
    ): DbAgentMessage?

    @Query("SELECT * FROM agent_conversations WHERE conversationId = :conversationId")
    suspend fun getConversation(conversationId: String): DbAgentConversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: DbAgentConversation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<DbAgentMessage>)

    @Query("DELETE FROM agent_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Query("DELETE FROM agent_conversations WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("UPDATE agent_conversations SET title = :title, titleGenerated = 1 WHERE conversationId = :conversationId")
    suspend fun updateGeneratedTitle(
        conversationId: String,
        title: String,
    )
}
