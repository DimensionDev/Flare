package dev.dimension.flare.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.data.database.app.model.DbSearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
public interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(searchHistory: DbSearchHistory)

    @Query("SELECT * FROM DbSearchHistory ORDER BY created_at DESC")
    public fun select(): Flow<List<DbSearchHistory>>

    @Query("DELETE FROM DbSearchHistory WHERE search = :search")
    public suspend fun delete(search: String)

    @Query("DELETE FROM DbSearchHistory")
    public suspend fun deleteAll()
}
