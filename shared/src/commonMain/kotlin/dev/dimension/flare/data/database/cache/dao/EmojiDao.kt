package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.DbEmojiHistory
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
internal interface EmojiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emoji: DbEmoji)

    @Query("SELECT * FROM DbEmoji WHERE host = :host")
    fun get(host: String): Flow<DbEmoji?>

    @Query("DELETE FROM DbEmoji")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(emoji: DbEmojiHistory)

    @Query("SELECT * FROM DbEmojiHistory WHERE accountKey = :accountKey ORDER BY lastUse DESC")
    suspend fun getHistory(accountKey: MicroBlogKey): List<DbEmojiHistory>

    @Query("DELETE FROM DbEmojiHistory")
    suspend fun clearHistory()
}
