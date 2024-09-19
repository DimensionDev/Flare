package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbSearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchHistory: DbSearchHistory)

    @Query("SELECT * FROM DbSearchHistory ORDER BY created_at DESC")
    fun select(): Flow<List<DbSearchHistory>>

    @Query("DELETE FROM DbSearchHistory WHERE search = :search")
    suspend fun delete(search: String)

    @Query("DELETE FROM DbSearchHistory")
    suspend fun deleteAll()
}
