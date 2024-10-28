package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import chat.bsky.convo.GetMessagesQueryParams
import chat.bsky.convo.GetMessagesResponseMessageUnion
import chat.bsky.convo.UpdateReadRequest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbMessageItemWithUser
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class DMConversationRemoteMediator(
    private val getService: suspend () -> BlueskyService,
    private val clearBadge: (roomKey: MicroBlogKey) -> Unit,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val roomKey: MicroBlogKey,
) : RemoteMediator<Int, DbMessageItemWithUser>() {
    private var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbMessageItemWithUser>,
    ): MediatorResult {
        try {
            val service = getService.invoke()
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        service.updateRead(
                            request =
                                UpdateReadRequest(
                                    convoId = roomKey.id,
                                ),
                        )
                        clearBadge.invoke(roomKey)
                        cursor = null
                        service.getMessages(
                            params =
                                GetMessagesQueryParams(
                                    convoId = roomKey.id,
                                    limit = state.config.pageSize.toLong(),
                                    cursor = null,
                                ),
                        )
                    }
                    LoadType.PREPEND -> {
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
//                        val firstItem =
//                            state.firstItemOrNull() ?: return MediatorResult.Success(
//                                endOfPaginationReached = true,
//                            )
//                        val message =
//                            database.messageDao().getMessage(firstItem.message.messageKey)
//                                ?: return MediatorResult.Success(
//                                    endOfPaginationReached = true,
//                                )
//                        val content = message.content
//                        if (content !is MessageContent.Bluesky) {
//                            return MediatorResult.Success(
//                                endOfPaginationReached = true,
//                            )
//                        }
//                        val rev =
//                            when (content) {
//                                is MessageContent.Bluesky.Deleted -> content.data.rev
//                                is MessageContent.Bluesky.Message -> content.data.rev
//                            }
//                        service.getMessages(
//                            params =
//                                GetMessagesQueryParams(
//                                    convoId = roomKey.id,
//                                    limit = state.config.pageSize.toLong(),
//                                    cursor = rev,
//                                ),
//                        )
                    }
                    LoadType.APPEND -> {
//                        val message =
//                            database.messageDao().getLatestMessage(roomKey)
//                                ?: return MediatorResult.Success(
//                                    endOfPaginationReached = true,
//                                )
//                        val content = message.content
//                        if (content !is MessageContent.Bluesky) {
//                            return MediatorResult.Success(
//                                endOfPaginationReached = true,
//                            )
//                        }
//                        val rev =
//                            when (content) {
//                                is MessageContent.Bluesky.Deleted -> content.data.rev
//                                is MessageContent.Bluesky.Message -> content.data.rev
//                            }
                        service.getMessages(
                            params =
                                GetMessagesQueryParams(
                                    convoId = roomKey.id,
                                    limit = state.config.pageSize.toLong(),
                                    cursor = cursor,
                                ),
                        )
                    }
                }.requireResponse()
            if (loadType == LoadType.REFRESH) {
                database.messageDao().clearRoomMessage(roomKey = roomKey)
            }
            cursor = response.cursor
            Bluesky.saveMessage(
                accountKey = accountKey,
                database = database,
                // TODO: handle deleted messages
                data =
                    response.messages.filterIsInstance<GetMessagesResponseMessageUnion.MessageView>().map {
                        it.value
                    },
                roomKey = roomKey,
            )
            return MediatorResult.Success(
                endOfPaginationReached = cursor == null,
            )
        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }
}
