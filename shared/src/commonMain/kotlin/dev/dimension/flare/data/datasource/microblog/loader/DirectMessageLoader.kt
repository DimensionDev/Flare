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

public interface DirectMessageLoader {
    public val platformType: PlatformType

    public val runtimeTransformer: Flow<DirectMessageRuntimeTransformer>
        get() = flowOf(DirectMessageRuntimeTransformer())

    public suspend fun loadRooms(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMRoom>

    public suspend fun loadMessages(
        roomKey: MicroBlogKey,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMItem>

    public suspend fun fetchRoomInfo(roomKey: MicroBlogKey): UiDMRoom

    public suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    ): UiDMItem

    public suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )

    public suspend fun fetchNewMessages(
        roomKey: MicroBlogKey,
        cursor: String?,
    ): DirectMessageDelta

    public suspend fun leaveRoom(roomKey: MicroBlogKey)

    public fun createRoom(userKey: MicroBlogKey): Flow<UiState<UiDMRoom>>

    public suspend fun canSend(userKey: MicroBlogKey): Boolean

    public suspend fun loadBadgeCount(): Int
}

public data class DirectMessageDelta(
    val messages: List<UiDMItem> = emptyList(),
    val deletedMessageKeys: List<MicroBlogKey> = emptyList(),
)

public data class DirectMessageRuntimeTransformer(
    val room: (UiDMRoom) -> UiDMRoom = { it },
    val item: (UiDMItem) -> UiDMItem = { it },
)
