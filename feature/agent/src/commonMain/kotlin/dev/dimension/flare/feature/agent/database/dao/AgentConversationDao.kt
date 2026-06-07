package dev.dimension.flare.feature.agent.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.feature.agent.database.model.DbAgentConversation
import dev.dimension.flare.feature.agent.database.model.DbAgentConversationAttachment
import dev.dimension.flare.feature.agent.database.model.DbAgentMessage
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AgentConversationDao {
    @Query("SELECT * FROM agent_conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<DbAgentConversation>>

    @Query("SELECT * FROM agent_conversations WHERE conversationId = :conversationId")
    fun observeConversation(conversationId: String): Flow<DbAgentConversation?>

    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId ORDER BY position ASC")
    fun observeMessages(conversationId: String): Flow<List<DbAgentMessage>>

    @Query(
        """
        SELECT * FROM agent_conversation_attachments
        WHERE conversationId = :conversationId
            AND owner = :owner
            AND groupKey = :groupKey
        ORDER BY position ASC
        """,
    )
    fun observeAttachments(
        conversationId: String,
        owner: String,
        groupKey: String,
    ): Flow<List<DbAgentConversationAttachment>>

    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId ORDER BY position ASC")
    suspend fun getMessages(conversationId: String): List<DbAgentMessage>

    @Query("SELECT * FROM agent_conversations WHERE conversationId = :conversationId")
    suspend fun getConversation(conversationId: String): DbAgentConversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: DbAgentConversation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<DbAgentMessage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<DbAgentConversationAttachment>)

    @Query("DELETE FROM agent_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Query(
        """
        DELETE FROM agent_conversation_attachments
        WHERE conversationId = :conversationId
            AND owner = :owner
            AND groupKey = :groupKey
        """,
    )
    suspend fun deleteAttachmentGroup(
        conversationId: String,
        owner: String,
        groupKey: String,
    )

    @Query("DELETE FROM agent_conversation_attachments WHERE conversationId = :conversationId")
    suspend fun deleteAttachments(conversationId: String)

    @Query("DELETE FROM agent_conversations WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("UPDATE agent_conversations SET title = :title, titleGenerated = 1 WHERE conversationId = :conversationId")
    suspend fun updateGeneratedTitle(
        conversationId: String,
        title: String,
    )
}
