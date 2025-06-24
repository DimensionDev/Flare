package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import chat.bsky.convo.GetMessagesQueryParams
import chat.bsky.convo.GetMessagesResponseMessageUnion
import chat.bsky.convo.UpdateReadRequest
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
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
) : BaseRemoteMediator<Int, DbMessageItemWithUser>() {
    private var cursor: String? = null

    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbMessageItemWithUser>,
    ): MediatorResult {
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
                }
                LoadType.APPEND -> {
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
        cursor = response.cursor
        database.connect {
            if (loadType == LoadType.REFRESH) {
                database.messageDao().clearRoomMessage(roomKey = roomKey)
            }
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
        }
        return MediatorResult.Success(
            endOfPaginationReached = cursor == null,
        )
    }
}
