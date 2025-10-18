package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbRssSources
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RssSourceDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(data: DbRssSources)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<DbRssSources>)

    @Query("SELECT * FROM DbRssSources")
    fun getAll(): Flow<List<DbRssSources>>

    @Query("DELETE FROM DbRssSources WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM DbRssSources WHERE id = :id")
    fun get(id: Int): Flow<DbRssSources>

    @Query("SELECT * FROM DbRssSources WHERE url = :url")
    suspend fun getByUrl(url: String): List<DbRssSources>
}
