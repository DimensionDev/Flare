package dev.dimension.flare.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.SubscriptionType
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RssSourceDao {
    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun insert(data: DbRssSources)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<DbRssSources>)

    @Query("SELECT * FROM DbRssSources")
    fun getAll(): Flow<List<DbRssSources>>

    @Query("DELETE FROM DbRssSources WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM DbRssSources WHERE id = :id")
    fun get(id: Int): Flow<DbRssSources>

    @Query("SELECT * FROM DbRssSources WHERE url = :url")
    suspend fun getByUrl(url: String): List<DbRssSources>

    @Query("SELECT * FROM DbRssSources WHERE url = :url AND type = :type")
    suspend fun getByUrlAndType(
        url: String,
        type: SubscriptionType,
    ): List<DbRssSources>
}
