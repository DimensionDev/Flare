package dev.dimension.flare.data.database.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.dimension.flare.data.database.app.model.DbKeywordFilter
import kotlinx.coroutines.flow.Flow

@Dao
internal interface KeywordFilterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(keywordFilter: DbKeywordFilter)

    @Query("SELECT * FROM DbKeywordFilter")
    fun selectAll(): Flow<List<DbKeywordFilter>>

    @Query("SELECT * FROM DbKeywordFilter WHERE expired_at = 0 OR expired_at > :currentTime")
    fun selectAllNotExpired(currentTime: Long): Flow<List<DbKeywordFilter>>

    @Query(
        "SELECT * FROM DbKeywordFilter WHERE (expired_at = 0 OR expired_at > :currentTime) AND (for_timeline = :forTimeline OR for_notification = :forNotification OR for_search = :forSearch)",
    )
    fun selectNotExpiredFor(
        currentTime: Long,
        forTimeline: Long,
        forNotification: Long,
        forSearch: Long,
    ): Flow<List<DbKeywordFilter>>

    @Query("SELECT * FROM DbKeywordFilter WHERE keyword = :keyword")
    fun selectByKeyword(keyword: String): Flow<DbKeywordFilter?>

    @Query("DELETE FROM DbKeywordFilter WHERE keyword = :keyword")
    suspend fun deleteByKeyword(keyword: String)

    @Query("DELETE FROM DbKeywordFilter")
    suspend fun deleteAll()

    @Query(
        "UPDATE DbKeywordFilter SET for_timeline = :forTimeline, for_notification = :forNotification, for_search = :forSearch, expired_at = :expiredAt WHERE keyword = :keyword",
    )
    suspend fun update(
        keyword: String,
        forTimeline: Long,
        forNotification: Long,
        forSearch: Long,
        expiredAt: Long,
    )
}
