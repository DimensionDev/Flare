package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbRssSources
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RssSourceDao {
    @Insert
    suspend fun insert(data: DbRssSources)

    @Query("SELECT * FROM DbRssSources")
    fun getAll(): Flow<List<DbRssSources>>

    @Query("DELETE FROM DbRssSources WHERE url = :url")
    suspend fun delete(url: String)
}
