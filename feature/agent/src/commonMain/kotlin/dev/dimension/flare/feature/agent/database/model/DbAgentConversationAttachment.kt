package dev.dimension.flare.feature.agent.database.model

import androidx.room3.Entity

@Entity(
    tableName = "agent_conversation_attachments",
    primaryKeys = ["conversationId", "owner", "groupKey", "position"],
)
internal data class DbAgentConversationAttachment(
    val conversationId: String,
    val owner: String,
    val groupKey: String,
    val position: Int,
    val type: String,
    val contentJson: String,
    val createdAt: Long,
)
