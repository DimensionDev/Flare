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
    @Query("SELECT * FROM paging_timeline WHERE pagingKey == :pagingKey AND accountKey == :accountKey ORDER BY sortId DESC")
    fun getPagingSource(
        pagingKey: String,
        accountKey: MicroBlogKey,
    ): PagingSource<Int, DbPagingTimelineWithStatus>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeline: List<DbPagingTimeline>)

    @Query("SELECT EXISTS(SELECT * FROM paging_timeline WHERE pagingKey == :pagingKey AND accountKey == :accountKey)")
    suspend fun exists(
        pagingKey: String,
        accountKey: MicroBlogKey,
    ): Boolean

    @Delete
    suspend fun delete(timeline: List<DbPagingTimeline>)

    @Query("DELETE FROM paging_timeline WHERE pagingKey == :pagingKey AND accountKey == :accountKey")
    suspend fun delete(
        pagingKey: String,
        accountKey: MicroBlogKey,
    )
}
