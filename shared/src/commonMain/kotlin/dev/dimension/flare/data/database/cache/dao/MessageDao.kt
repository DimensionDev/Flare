package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbMessageRoom
import dev.dimension.flare.data.database.cache.model.DbMessageRoomReference
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MessageDao {
    @Query("SELECT * FROM DbDirectMessageTimeline WHERE accountType = :accountType ORDER BY sortId DESC")
    fun getRoomTimeline(accountType: DbAccountType): Flow<List<DbDirectMessageTimeline>>

    @Query("SELECT * FROM DbDirectMessageTimeline WHERE roomKey = :roomKey AND accountType = :accountType")
    fun getRoomInfo(
        roomKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbDirectMessageTimeline?>

    @Query(
        """
        SELECT * FROM DbDirectMessageTimeline
        WHERE accountType = :accountType
        ORDER BY sortId DESC, _id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getRoomTimelinePage(
        accountType: DbAccountType,
        offset: Int,
        limit: Int,
    ): List<DbDirectMessageTimeline>

    @Query(
        """
        SELECT * FROM DbMessageItem
        WHERE roomKey = :roomKey
        ORDER BY timestamp DESC, messageKey DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getRoomMessagesPage(
        roomKey: MicroBlogKey,
        offset: Int,
        limit: Int,
    ): List<DbMessageItem>

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<DbMessageRoom>)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun insertReferences(items: List<DbMessageRoomReference>)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun insertMessages(items: List<DbMessageItem>)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun insertTimeline(items: List<DbDirectMessageTimeline>)

    @Query("DELETE FROM DbMessageItem WHERE roomKey = :roomKey")
    suspend fun clearRoomMessage(roomKey: MicroBlogKey)

    @Query("DELETE FROM DbMessageItem WHERE messageKey = :messageKey")
    suspend fun deleteMessage(messageKey: MicroBlogKey)

    @Query("SELECT * FROM DbMessageItem WHERE messageKey = :messageKey")
    suspend fun getMessage(messageKey: MicroBlogKey): DbMessageItem?

    @Query("SELECT * FROM DbMessageItem WHERE roomKey = :roomKey AND isLocal = 0 ORDER BY timestamp DESC")
    suspend fun getLatestMessage(roomKey: MicroBlogKey): DbMessageItem?

    @Query("DELETE FROM DbDirectMessageTimeline WHERE accountType = :accountType")
    suspend fun clearMessageTimeline(accountType: DbAccountType)

    @Query("UPDATE DbDirectMessageTimeline SET unreadCount = 0 WHERE roomKey = :roomKey AND accountType = :accountType")
    suspend fun clearUnreadCount(
        roomKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbDirectMessageTimeline WHERE roomKey = :roomKey AND accountType = :accountType")
    suspend fun deleteRoomTimeline(
        roomKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbMessageRoom WHERE roomKey = :roomKey")
    suspend fun deleteRoom(roomKey: MicroBlogKey)

    @Query("DELETE FROM DbMessageRoomReference WHERE roomKey = :roomKey")
    suspend fun deleteRoomReference(roomKey: MicroBlogKey)

    @Query("DELETE FROM DbMessageItem WHERE roomKey = :roomKey")
    suspend fun deleteRoomMessages(roomKey: MicroBlogKey)
}
