package dev.dimension.flare.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.data.database.app.model.DbKeywordFilter
import kotlinx.coroutines.flow.Flow

@Dao
public interface KeywordFilterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(keywordFilter: DbKeywordFilter)

    @Query("SELECT * FROM DbKeywordFilter")
    public fun selectAll(): Flow<List<DbKeywordFilter>>

    @Query("SELECT * FROM DbKeywordFilter WHERE expired_at = 0 OR expired_at > :currentTime")
    public fun selectAllNotExpired(currentTime: Long): Flow<List<DbKeywordFilter>>

    @Query(
        "SELECT * FROM DbKeywordFilter WHERE (expired_at = 0 OR expired_at > :currentTime) AND (for_timeline = :forTimeline OR for_notification = :forNotification OR for_search = :forSearch)",
    )
    public fun selectNotExpiredFor(
        currentTime: Long,
        forTimeline: Long,
        forNotification: Long,
        forSearch: Long,
    ): Flow<List<DbKeywordFilter>>

    @Query("SELECT * FROM DbKeywordFilter WHERE keyword = :keyword")
    public fun selectByKeyword(keyword: String): Flow<DbKeywordFilter?>

    @Query("DELETE FROM DbKeywordFilter WHERE keyword = :keyword")
    public suspend fun deleteByKeyword(keyword: String)

    @Query("DELETE FROM DbKeywordFilter")
    public suspend fun deleteAll()

    @Query(
        "UPDATE DbKeywordFilter SET for_timeline = :forTimeline, for_notification = :forNotification, for_search = :forSearch, expired_at = :expiredAt WHERE keyword = :keyword",
    )
    public suspend fun update(
        keyword: String,
        forTimeline: Long,
        forNotification: Long,
        forSearch: Long,
        expiredAt: Long,
    )
}
