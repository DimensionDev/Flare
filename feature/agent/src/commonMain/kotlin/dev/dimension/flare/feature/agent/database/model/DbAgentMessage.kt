package dev.dimension.flare.feature.agent.database.model

import androidx.room3.Entity

@Entity(
    tableName = "agent_messages",
    primaryKeys = ["conversationId", "position"],
)
internal data class DbAgentMessage(
    val conversationId: String,
    val position: Int,
    val role: String,
    val text: String,
    val messageJson: String,
    val createdAt: Long,
)
