package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PagingTimelineDao {
    @Transaction
    @Query(
        "SELECT DbPagingTimeline.* FROM DbPagingTimeline " +
            "INNER JOIN DbStatus ON DbStatus.statusKey = DbPagingTimeline.statusKey " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey AND DbStatus.accountType = :accountType " +
            "ORDER BY DbPagingTimeline.sortId",
    )
    fun getPagingSource(
        pagingKey: String,
        accountType: DbAccountType,
    ): PagingSource<Int, DbPagingTimelineWithStatus>

    @Transaction
    @Query(
        "SELECT * FROM DbPagingTimeline WHERE pagingKey = :pagingKey ORDER BY sortId",
    )
    fun getPagingSource(pagingKey: String): PagingSource<Int, DbPagingTimelineWithStatus>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT * FROM DbStatus " +
            "WHERE DbStatus.text like :query",
    )
    fun searchHistoryPagingSource(query: String): PagingSource<Int, DbStatusWithReference>

    @Transaction
    @Query(
        "SELECT DbPagingTimeline.* FROM DbPagingTimeline " +
            "INNER JOIN DbStatus ON DbStatus.statusKey = DbPagingTimeline.statusKey " +
            "WHERE DbPagingTimeline.pagingKey = :pagingKey AND DbStatus.accountType = :accountType " +
            "LIMIT 1",
    )
    fun get(
        pagingKey: String,
        accountType: DbAccountType,
    ): Flow<DbPagingTimelineWithStatus?>

    @Transaction
    @Query("SELECT * FROM DbPagingTimeline WHERE pagingKey = :pagingKey ORDER BY sortId DESC")
    fun getStatusHistoryPagingSource(pagingKey: String): PagingSource<Int, DbPagingTimelineWithStatus>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeline: List<DbPagingTimeline>)

    @Query(
        "SELECT * FROM DbPagingTimeline " +
            "WHERE pagingKey = :pagingKey AND statusKey IN (:statusKeys)",
    )
    suspend fun getByPagingKeyAndStatusKeys(
        pagingKey: String,
        statusKeys: List<MicroBlogKey>,
    ): List<DbPagingTimeline>

    @Query(
        "SELECT * FROM DbPagingTimeline " +
            "WHERE pagingKey = :pagingKey ORDER BY sortId",
    )
    suspend fun getByPagingKey(pagingKey: String): List<DbPagingTimeline>

    @Delete
    suspend fun delete(timeline: List<DbPagingTimeline>)

    @Query(
        "DELETE FROM DbPagingTimeline WHERE pagingKey = :pagingKey " +
            "AND EXISTS(" +
            "SELECT 1 FROM DbStatus " +
            "WHERE DbStatus.statusKey = DbPagingTimeline.statusKey " +
            "AND DbStatus.accountType = :accountType" +
            ")",
    )
    suspend fun delete(
        pagingKey: String,
        accountType: DbAccountType,
    )

    suspend fun delete(
        pagingKey: String,
        accountKey: MicroBlogKey,
    ) {
        delete(pagingKey, AccountType.Specific(accountKey))
    }

    /**
     * Should be used to delete a specific paging timeline by its key.
     */
    @Query("DELETE FROM DbPagingTimeline WHERE pagingKey = :pagingKey")
    suspend fun delete(pagingKey: String)

    @Query(
        "DELETE FROM DbPagingTimeline " +
            "WHERE EXISTS(" +
            "SELECT 1 FROM DbStatus " +
            "WHERE DbStatus.statusKey = DbPagingTimeline.statusKey " +
            "AND DbStatus.accountType = :accountType" +
            ")",
    )
    suspend fun deleteByAccountType(accountType: DbAccountType)

    @Query(
        "DELETE FROM DbPagingTimeline " +
            "WHERE statusKey = :statusKey " +
            "AND EXISTS(" +
            "SELECT 1 FROM DbStatus " +
            "WHERE DbStatus.statusKey = DbPagingTimeline.statusKey " +
            "AND DbStatus.accountType = :accountType" +
            ")",
    )
    suspend fun deleteStatus(
        accountType: DbAccountType,
        statusKey: MicroBlogKey,
    )

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM DbPagingTimeline " +
            "WHERE pagingKey = :paging_key " +
            "AND EXISTS(" +
            "SELECT 1 FROM DbStatus " +
            "WHERE DbStatus.statusKey = DbPagingTimeline.statusKey " +
            "AND DbStatus.accountType = :accountType" +
            ")" +
            ")",
    )
    suspend fun existsPaging(
        accountType: DbAccountType,
        paging_key: String,
    ): Boolean

    suspend fun existsPaging(
        account_key: MicroBlogKey,
        paging_key: String,
    ): Boolean =
        existsPaging(
            accountType = AccountType.Specific(account_key),
            paging_key = paging_key,
        )

    @Query("DELETE FROM DbPagingTimeline")
    suspend fun clear()

    @Query("SELECT EXISTS(SELECT 1 FROM DbPagingTimeline WHERE pagingKey = :pagingKey)")
    suspend fun anyPaging(pagingKey: String): Boolean

    @Query("SELECT * FROM DbPagingKey WHERE pagingKey = :pagingKey LIMIT 1")
    suspend fun getPagingKey(pagingKey: String): DbPagingKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPagingKey(pagingKey: DbPagingKey)

    @Query("DELETE FROM DbPagingKey WHERE pagingKey = :pagingKey")
    suspend fun deletePagingKey(pagingKey: String)

    @Query("UPDATE DbPagingKey SET nextKey = :nextKey WHERE pagingKey = :pagingKey")
    suspend fun updatePagingKeyNextKey(
        pagingKey: String,
        nextKey: String,
    )

    @Query("UPDATE DbPagingKey SET prevKey = :prevKey WHERE pagingKey = :pagingKey")
    suspend fun updatePagingKeyPrevKey(
        pagingKey: String,
        prevKey: String,
    )
}
