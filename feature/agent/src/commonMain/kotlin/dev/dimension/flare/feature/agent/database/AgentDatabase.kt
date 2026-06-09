package dev.dimension.flare.feature.agent.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import dev.dimension.flare.feature.agent.database.dao.AgentConversationDao
import dev.dimension.flare.feature.agent.database.model.DbAgentConversation
import dev.dimension.flare.feature.agent.database.model.DbAgentConversationAttachment
import dev.dimension.flare.feature.agent.database.model.DbAgentMessage

@Database(
    entities = [
        DbAgentConversation::class,
        DbAgentConversationAttachment::class,
        DbAgentMessage::class,
    ],
    version = 4,
    exportSchema = false,
)
@ConstructedBy(AgentDatabaseConstructor::class)
internal abstract class AgentDatabase : RoomDatabase() {
    abstract fun conversationDao(): AgentConversationDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object AgentDatabaseConstructor : RoomDatabaseConstructor<AgentDatabase> {
    override fun initialize(): AgentDatabase
}

internal suspend fun <R> RoomDatabase.connect(block: suspend () -> R): R =
    useWriterConnection {
        it.immediateTransaction {
            block.invoke()
        }
    }
