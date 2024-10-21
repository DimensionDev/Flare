package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimelineWithRoom
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbMessageItemWithUser
import dev.dimension.flare.data.database.cache.model.DbMessageRoom
import dev.dimension.flare.data.database.cache.model.DbMessageRoomReference
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM DbDirectMessageTimeline WHERE accountKey = :accountKey ORDER BY sortId DESC")
    fun getRoomPagingSource(accountKey: MicroBlogKey): PagingSource<Int, DbDirectMessageTimelineWithRoom>

    @Transaction
    @Query("SELECT * FROM DbMessageItem WHERE roomKey = :roomKey ORDER BY timestamp DESC")
    fun getRoomMessagesPagingSource(roomKey: MicroBlogKey): PagingSource<Int, DbMessageItemWithUser>

    @Transaction
    @Query("SELECT * FROM DbDirectMessageTimeline WHERE roomKey = :roomKey AND accountKey = :accountKey")
    fun getRoomInfo(
        roomKey: MicroBlogKey,
        accountKey: MicroBlogKey,
    ): Flow<DbDirectMessageTimelineWithRoom?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<DbMessageRoom>)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertReferences(items: List<DbMessageRoomReference>)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertMessages(items: List<DbMessageItem>)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertTimeline(items: List<DbDirectMessageTimeline>)

    @Query("DELETE FROM DbDirectMessageTimeline WHERE roomKey = :roomKey AND accountKey = :accountKey")
    suspend fun deleteTimeline(
        roomKey: MicroBlogKey,
        accountKey: MicroBlogKey,
    )

    @Query("DELETE FROM DbMessageItem WHERE messageKey = :messageKey")
    suspend fun deleteMessage(messageKey: MicroBlogKey)

    @Query("SELECT * FROM DbMessageItem WHERE messageKey = :messageKey")
    suspend fun getMessage(messageKey: MicroBlogKey): DbMessageItem?

    @Query("SELECT * FROM DbMessageItem WHERE roomKey = :roomKey AND isLocal = 0 ORDER BY timestamp DESC")
    suspend fun getLatestMessage(roomKey: MicroBlogKey): DbMessageItem?
}
