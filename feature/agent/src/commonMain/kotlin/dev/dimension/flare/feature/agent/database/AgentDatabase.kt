package dev.dimension.flare.feature.agent.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.immediateTransaction
import androidx.room3.migration.Migration
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import dev.dimension.flare.feature.agent.database.dao.AgentConversationDao
import dev.dimension.flare.feature.agent.database.model.DbAgentContext
import dev.dimension.flare.feature.agent.database.model.DbAgentConversation
import dev.dimension.flare.feature.agent.database.model.DbAgentMessage

@Database(
    entities = [
        DbAgentConversation::class,
        DbAgentMessage::class,
        DbAgentContext::class,
    ],
    version = 10,
    exportSchema = false,
)
@ConstructedBy(AgentDatabaseConstructor::class)
internal abstract class AgentDatabase : RoomDatabase() {
    abstract fun conversationDao(): AgentConversationDao
}

internal val AGENT_CONTEXT_MIGRATION =
    object : Migration(9, 10) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.executeSQL(
                "CREATE TABLE IF NOT EXISTS agent_contexts (conversationId TEXT NOT NULL PRIMARY KEY, summary TEXT NOT NULL, throughPosition INTEGER NOT NULL)",
            )
        }
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
