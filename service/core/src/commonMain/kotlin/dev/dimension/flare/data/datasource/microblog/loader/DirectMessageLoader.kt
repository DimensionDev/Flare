package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom

public interface DirectMessageLoader {
    public suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    )

    public suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )

    public suspend fun leaveConversation(roomKey: MicroBlogKey)

    public suspend fun createRoom(userKey: MicroBlogKey): MicroBlogKey

    public suspend fun canSendMessage(userKey: MicroBlogKey): Boolean

    public suspend fun fetchBadgeCount(): Int

    public suspend fun loadRoomList(
        pageSize: Int,
        cursor: String?,
    ): PagingResult<UiDMRoom>

    public suspend fun loadConversation(
        roomKey: MicroBlogKey,
        pageSize: Int,
        cursor: String?,
    ): PagingResult<UiDMItem>

    public suspend fun loadConversationInfo(roomKey: MicroBlogKey): UiDMRoom

    public suspend fun fetchNewMessages(roomKey: MicroBlogKey): List<UiDMItem>
}
