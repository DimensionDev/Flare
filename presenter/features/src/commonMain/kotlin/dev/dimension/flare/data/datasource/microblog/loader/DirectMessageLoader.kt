package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal interface DirectMessageLoader {
    val platformType: PlatformType

    val runtimeTransformer: Flow<DirectMessageRuntimeTransformer>
        get() = flowOf(DirectMessageRuntimeTransformer())

    suspend fun loadRooms(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMRoom>

    suspend fun loadMessages(
        roomKey: MicroBlogKey,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMItem>

    suspend fun fetchRoomInfo(roomKey: MicroBlogKey): UiDMRoom

    suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    ): UiDMItem

    suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )

    suspend fun fetchNewMessages(
        roomKey: MicroBlogKey,
        cursor: String?,
    ): DirectMessageDelta

    suspend fun leaveRoom(roomKey: MicroBlogKey)

    fun createRoom(userKey: MicroBlogKey): Flow<UiState<UiDMRoom>>

    suspend fun canSend(userKey: MicroBlogKey): Boolean

    suspend fun loadBadgeCount(): Int
}

internal data class DirectMessageDelta(
    val messages: List<UiDMItem> = emptyList(),
    val deletedMessageKeys: List<MicroBlogKey> = emptyList(),
)

internal data class DirectMessageRuntimeTransformer(
    val room: (UiDMRoom) -> UiDMRoom = { it },
    val item: (UiDMItem) -> UiDMItem = { it },
)
