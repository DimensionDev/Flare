package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.model.DbMessageItemWithUser
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey

internal class DMConversationRemoteMediator(
    private val service: XQTService,
    private val clearBadge: (roomKey: MicroBlogKey, lastReadId: String) -> Unit,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val roomKey: MicroBlogKey,
) : BaseRemoteMediator<Int, DbMessageItemWithUser>() {
    private var cursor: String? = null

    @OptIn(ExperimentalPagingApi::class)
    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbMessageItemWithUser>,
    ): MediatorResult {
        if (loadType == LoadType.PREPEND) {
            return MediatorResult.Success(
                endOfPaginationReached = true,
            )
        }
        val response = service.getDMConversationTimeline(conversationId = roomKey.id, maxId = cursor)
        cursor = response.conversationTimeline?.minEntryId
        database.connect {
            if (loadType == LoadType.REFRESH) {
                database.messageDao().clearRoomMessage(roomKey = roomKey)
            }
            XQT.saveDM(
                accountKey = accountKey,
                database = database,
                propertyEntries = response.conversationTimeline?.propertyEntries,
                users = response.conversationTimeline?.users,
                updateRoom = false,
                conversations = response.conversationTimeline?.conversations,
            )
        }
        if (loadType == LoadType.REFRESH) {
            clearBadge.invoke(roomKey, response.conversationTimeline?.maxEntryId.orEmpty())
            service.postDMConversationMarkRead(
                conversationId = roomKey.id,
                conversationId2 = roomKey.id,
                lastReadEventId = response.conversationTimeline?.maxEntryId.orEmpty(),
            )
        }
        return MediatorResult.Success(
            endOfPaginationReached = response.conversationTimeline?.status == "AT_END",
        )
    }
}
