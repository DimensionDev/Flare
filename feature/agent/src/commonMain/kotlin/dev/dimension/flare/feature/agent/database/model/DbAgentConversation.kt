package dev.dimension.flare.feature.agent.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "agent_conversations")
internal data class DbAgentConversation(
    @PrimaryKey
    val conversationId: String,
    val title: String?,
    val titleGenerated: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val isRunning: Boolean,
    val currentTraceJson: String?,
    val traceHistoryJson: String,
    val errorMessage: String?,
)
