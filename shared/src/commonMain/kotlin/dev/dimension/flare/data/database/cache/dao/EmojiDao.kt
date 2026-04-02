package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.DbEmojiHistory
import dev.dimension.flare.model.DbAccountType
import kotlinx.coroutines.flow.Flow

@Dao
internal interface EmojiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emoji: DbEmoji)

    @Query("SELECT * FROM DbEmoji WHERE host = :host")
    fun get(host: String): Flow<DbEmoji?>

    @Query("SELECT content FROM DbEmoji WHERE host = :host")
    fun getContent(host: String): Flow<ByteArray?>

    @Query("DELETE FROM DbEmoji WHERE host = :host")
    suspend fun delete(host: String)

    @Query("DELETE FROM DbEmoji")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(emoji: DbEmojiHistory)

    @Query("SELECT * FROM DbEmojiHistory WHERE accountType = :accountType ORDER BY lastUse DESC")
    suspend fun getHistory(accountType: DbAccountType): List<DbEmojiHistory>

    @Query("DELETE FROM DbEmojiHistory")
    suspend fun clearHistory()

    @Query("DELETE FROM DbEmojiHistory WHERE accountType = :accountType")
    suspend fun clearHistoryByAccountType(accountType: DbAccountType)
}
