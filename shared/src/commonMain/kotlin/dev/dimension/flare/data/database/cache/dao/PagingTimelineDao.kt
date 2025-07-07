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

@Dao
internal interface PagingTimelineDao {
    @Transaction
    @Query(
        "SELECT * FROM DbPagingTimeline WHERE pagingKey = :pagingKey AND accountType = :accountType ORDER BY sortId DESC",
    )
    fun getPagingSource(
        pagingKey: String,
        accountType: DbAccountType,
    ): PagingSource<Int, DbPagingTimelineWithStatus>

    @Transaction
    @Query(
        "SELECT * FROM DbPagingTimeline WHERE pagingKey = :pagingKey ORDER BY sortId DESC",
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
    @Query("SELECT * FROM DbPagingTimeline WHERE pagingKey = :pagingKey ORDER BY sortId DESC")
    fun getStatusHistoryPagingSource(pagingKey: String): PagingSource<Int, DbPagingTimelineWithStatus>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeline: List<DbPagingTimeline>)

    @Delete
    suspend fun delete(timeline: List<DbPagingTimeline>)

    @Query("DELETE FROM DbPagingTimeline WHERE pagingKey = :pagingKey AND accountType = :accountType")
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

    @Query("DELETE FROM DbPagingTimeline WHERE accountType = :accountType")
    suspend fun deleteByAccountType(accountType: DbAccountType)

    @Query("DELETE FROM DbPagingTimeline WHERE accountType = :accountType AND statusKey = :statusKey")
    suspend fun deleteStatus(
        accountType: DbAccountType,
        statusKey: MicroBlogKey,
    )

    suspend fun deleteStatus(
        statusKey: MicroBlogKey,
        accountKey: MicroBlogKey,
    ) = deleteStatus(
        accountType = AccountType.Specific(accountKey),
        statusKey = statusKey,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM DbPagingTimeline WHERE accountType = :accountType AND pagingKey = :paging_key)")
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

    @Transaction
    @Query("SELECT * FROM DbPagingTimeline WHERE pagingKey = :pagingKey ORDER BY sortId ASC LIMIT 1")
    suspend fun getLastPagingTimeline(pagingKey: String): DbPagingTimelineWithStatus?

    @Query("SELECT * FROM DbPagingKey WHERE pagingKey = :pagingKey LIMIT 1")
    suspend fun getPagingKey(pagingKey: String): DbPagingKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPagingKey(pagingKey: DbPagingKey)

    @Query("DELETE FROM DbPagingKey WHERE pagingKey = :pagingKey")
    suspend fun deletePagingKey(pagingKey: String)
}
