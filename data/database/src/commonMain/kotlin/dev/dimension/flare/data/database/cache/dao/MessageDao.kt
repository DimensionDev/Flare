package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimelineWithRoom
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbMessageItemWithUser
import dev.dimension.flare.data.database.cache.model.DbMessageRoom
import dev.dimension.flare.data.database.cache.model.DbMessageRoomReference
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
public interface MessageDao {
    @Transaction
    @Query("SELECT * FROM DbDirectMessageTimeline WHERE accountType = :accountType ORDER BY sortId DESC")
    public fun getRoomPagingSource(accountType: DbAccountType): PagingSource<Int, DbDirectMessageTimelineWithRoom>

    @Transaction
    @Query("SELECT * FROM DbDirectMessageTimeline WHERE accountType = :accountType ORDER BY sortId DESC")
    public fun getRoomTimeline(accountType: DbAccountType): Flow<List<DbDirectMessageTimelineWithRoom>>

    @Transaction
    @Query("SELECT * FROM DbMessageItem WHERE roomKey = :roomKey ORDER BY timestamp DESC")
    public fun getRoomMessagesPagingSource(roomKey: MicroBlogKey): PagingSource<Int, DbMessageItemWithUser>

    @Transaction
    @Query("SELECT * FROM DbDirectMessageTimeline WHERE roomKey = :roomKey AND accountType = :accountType")
    public fun getRoomInfo(
        roomKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbDirectMessageTimelineWithRoom?>

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    public suspend fun insert(items: List<DbMessageRoom>)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    public suspend fun insertReferences(items: List<DbMessageRoomReference>)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    public suspend fun insertMessages(items: List<DbMessageItem>)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    public suspend fun insertTimeline(items: List<DbDirectMessageTimeline>)

    @Query("DELETE FROM DbMessageItem WHERE roomKey = :roomKey")
    public suspend fun clearRoomMessage(roomKey: MicroBlogKey)

    @Query("DELETE FROM DbMessageItem WHERE messageKey = :messageKey")
    public suspend fun deleteMessage(messageKey: MicroBlogKey)

    @Query("SELECT * FROM DbMessageItem WHERE messageKey = :messageKey")
    public suspend fun getMessage(messageKey: MicroBlogKey): DbMessageItem?

    @Query("SELECT * FROM DbMessageItem WHERE roomKey = :roomKey AND isLocal = 0 ORDER BY timestamp DESC")
    public suspend fun getLatestMessage(roomKey: MicroBlogKey): DbMessageItem?

    @Query("DELETE FROM DbDirectMessageTimeline WHERE accountType = :accountType")
    public suspend fun clearMessageTimeline(accountType: DbAccountType)

    @Query("UPDATE DbDirectMessageTimeline SET unreadCount = 0 WHERE roomKey = :roomKey AND accountType = :accountType")
    public suspend fun clearUnreadCount(
        roomKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbDirectMessageTimeline WHERE roomKey = :roomKey AND accountType = :accountType")
    public suspend fun deleteRoomTimeline(
        roomKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbMessageRoom WHERE roomKey = :roomKey")
    public suspend fun deleteRoom(roomKey: MicroBlogKey)

    @Query("DELETE FROM DbMessageRoomReference WHERE roomKey = :roomKey")
    public suspend fun deleteRoomReference(roomKey: MicroBlogKey)

    @Query("DELETE FROM DbMessageItem WHERE roomKey = :roomKey")
    public suspend fun deleteRoomMessages(roomKey: MicroBlogKey)
}
