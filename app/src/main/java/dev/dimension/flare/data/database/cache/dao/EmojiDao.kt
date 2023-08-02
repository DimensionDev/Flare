package dev.dimension.flare.data.database.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.cache.model.DbEmoji
import kotlinx.coroutines.flow.Flow

@Dao
interface EmojiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(user: List<DbEmoji>)

    @Query("SELECT * FROM dbemoji WHERE host = :host")
    fun getEmoji(host: String): Flow<DbEmoji?>
}
