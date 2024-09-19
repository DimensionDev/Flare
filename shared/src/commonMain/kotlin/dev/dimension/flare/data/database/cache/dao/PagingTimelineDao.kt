package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.model.MicroBlogKey

@Dao
interface PagingTimelineDao {
    @Transaction
    @Query("SELECT * FROM DbPagingTimeline WHERE pagingKey == :pagingKey AND accountKey == :accountKey ORDER BY sortId DESC")
    fun getPagingSource(
        pagingKey: String,
        accountKey: MicroBlogKey,
    ): PagingSource<Int, DbPagingTimelineWithStatus>

    @Transaction
    @Query("SELECT * FROM DbPagingTimeline WHERE pagingKey == :pagingKey AND accountKey == :accountKey ORDER BY sortId DESC")
    fun getPaging(
        pagingKey: String,
        accountKey: MicroBlogKey,
    ): PagingSource<Int, DbPagingTimeline>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeline: List<DbPagingTimeline>)

    @Query("SELECT EXISTS(SELECT * FROM DbPagingTimeline WHERE pagingKey == :pagingKey AND accountKey == :accountKey)")
    suspend fun exists(
        pagingKey: String,
        accountKey: MicroBlogKey,
    ): Boolean

    @Delete
    suspend fun delete(timeline: List<DbPagingTimeline>)

    @Query("DELETE FROM DbPagingTimeline WHERE pagingKey == :pagingKey AND accountKey == :accountKey")
    suspend fun delete(
        pagingKey: String,
        accountKey: MicroBlogKey,
    )

    @Query("DELETE FROM DbPagingTimeline WHERE accountKey == :accountKey AND statusKey == :statusKey")
    suspend fun deleteStatus(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM DbPagingTimeline WHERE accountKey = :account_key AND pagingKey = :paging_key)")
    suspend fun existsPaging(
        account_key: MicroBlogKey,
        paging_key: String,
    ): Boolean

    @Query("DELETE FROM DbPagingTimeline")
    suspend fun clear()
}
