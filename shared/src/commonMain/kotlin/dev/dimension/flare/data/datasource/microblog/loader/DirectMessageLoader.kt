package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom

internal interface DirectMessageLoader {
    suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    )

    suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )

    suspend fun leaveConversation(roomKey: MicroBlogKey)

    suspend fun createRoom(userKey: MicroBlogKey): MicroBlogKey

    suspend fun canSendMessage(userKey: MicroBlogKey): Boolean

    suspend fun fetchBadgeCount(): Int

    suspend fun loadRoomList(
        pageSize: Int,
        cursor: String?,
    ): PagingResult<UiDMRoom>

    suspend fun loadConversation(
        roomKey: MicroBlogKey,
        pageSize: Int,
        cursor: String?,
    ): PagingResult<UiDMItem>

    suspend fun loadConversationInfo(roomKey: MicroBlogKey): UiDMRoom

    suspend fun fetchNewMessages(roomKey: MicroBlogKey): List<UiDMItem>
}
