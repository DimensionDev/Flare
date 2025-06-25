package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimelineWithRoom
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class DMListRemoteMediator(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
) : BaseRemoteMediator<Int, DbDirectMessageTimelineWithRoom>() {
    private var cursor: String? = null

    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbDirectMessageTimelineWithRoom>,
    ): MediatorResult {
        if (loadType == LoadType.REFRESH) {
            cursor = null
        } else if (loadType == LoadType.PREPEND) {
            return MediatorResult.Success(
                endOfPaginationReached = true,
            )
        }
        if (loadType == LoadType.REFRESH) {
            val response = service.getDMUserUpdates().inboxInitialState
            database.connect {
                database.messageDao().clearMessageTimeline(accountType = AccountType.Specific(accountKey))
                XQT.saveDM(
                    accountKey = accountKey,
                    database = database,
                    propertyEntries = response?.propertyEntries,
                    users = response?.users,
                    conversations = response?.conversations,
                )
            }
            cursor = response?.inboxTimelines?.trusted?.minEntryId
            return MediatorResult.Success(
                endOfPaginationReached = response?.inboxTimelines?.trusted?.status == "AT_END",
            )
        } else {
            val maxId = cursor
            if (maxId == null) {
                return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            }
            val response =
                service.getDMInboxTimelineTrusted(
                    maxId = maxId,
                )
            database.connect {
                XQT.saveDM(
                    accountKey = accountKey,
                    database = database,
                    propertyEntries = response.inboxTimeline?.propertyEntries,
                    users = response.inboxTimeline?.users,
                    conversations = response.inboxTimeline?.conversations,
                )
            }
            return MediatorResult.Success(
                endOfPaginationReached = response.inboxTimeline?.status == "AT_END",
            )
        }
//        val response = service.getDMInboxTimelineTrusted(
//            maxId = cursor,
//        ).inboxTimeline ?: return MediatorResult.Success(
//            endOfPaginationReached = true,
//        )
//        val pinned = if (loadType == LoadType.REFRESH) {
//            database.messageDao().clearMessageTimeline(accountKey = accountKey)
//            service.getDMPinnedInboxQuery().data?.labeledConversationSlice?.items.orEmpty()
//        } else {
//            emptyList()
//        }
//
//        cursor = response.minEntryId
//
//        XQT.saveDM(
//            accountKey = accountKey,
//            database = database,
//            data = response
//        )
//
//        return MediatorResult.Success(
//            endOfPaginationReached = response.status == "AT_END",
//        )
    }
}
