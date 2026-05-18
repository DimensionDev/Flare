package dev.dimension.flare.data.datasource.microblog.loader

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import androidx.paging.RemoteMediator
import androidx.paging.map
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@OptIn(ExperimentalPagingApi::class)
internal interface DirectMessageLoader {
    val platformType: PlatformType

    fun listRemoteMediator(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
    ): RemoteMediator<Int, DbDirectMessageTimeline>

    fun conversationRemoteMediator(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    ): RemoteMediator<Int, DbMessageItem>

    fun listFlow(
        source: Flow<PagingData<DbDirectMessageTimeline>>,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMRoom>> =
        source.map { paging ->
            paging.map { it.content.copy(unreadCount = it.unreadCount) }
        }

    fun conversationFlow(
        source: Flow<PagingData<DbMessageItem>>,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> =
        source.map { paging ->
            paging.map { it.content }
        }

    fun roomInfoFlow(source: Flow<DbDirectMessageTimeline?>): Flow<UiDMRoom> =
        source.mapNotNull { it?.content?.copy(unreadCount = it.unreadCount) }

    suspend fun fetchRoomInfo(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    )

    suspend fun sendMessage(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
        message: String,
    )

    suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )

    suspend fun fetchNewMessages(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    )

    suspend fun leaveRoom(roomKey: MicroBlogKey)

    fun createRoom(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): Flow<UiState<MicroBlogKey>>

    suspend fun canSend(userKey: MicroBlogKey): Boolean

    fun badgeCount(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
    ): CacheData<Int>
}
