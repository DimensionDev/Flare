package dev.dimension.flare.feature.agent.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "agent_contexts")
internal data class DbAgentContext(
    @PrimaryKey val conversationId: String,
    val summary: String,
    val throughPosition: Int,
)
