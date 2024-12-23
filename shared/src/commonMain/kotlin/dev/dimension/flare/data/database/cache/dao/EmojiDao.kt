package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbEmoji
import kotlinx.coroutines.flow.Flow

@Dao
internal interface EmojiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emoji: DbEmoji)

    @Query("SELECT * FROM DbEmoji WHERE host = :host")
    fun get(host: String): Flow<DbEmoji?>

    @Query("DELETE FROM DbEmoji")
    suspend fun clear()
}
