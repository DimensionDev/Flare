package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import chat.bsky.convo.ListConvosQueryParams
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimelineWithRoom
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class DMListRemoteMediator(
    private val getService: suspend () -> BlueskyService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : RemoteMediator<Int, DbDirectMessageTimelineWithRoom>() {
    private var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbDirectMessageTimelineWithRoom>,
    ): MediatorResult {
        try {
            val service = getService()
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        cursor = null
                        service
                            .listConvos(
                                params =
                                    ListConvosQueryParams(
                                        limit = state.config.pageSize.toLong(),
                                    ),
                            ).requireResponse()
                    }
                    LoadType.PREPEND ->
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    LoadType.APPEND -> {
                        service
                            .listConvos(
                                params =
                                    ListConvosQueryParams(
                                        limit = state.config.pageSize.toLong(),
                                        cursor = cursor,
                                    ),
                            ).requireResponse()
                    }
                }
            if (loadType == LoadType.REFRESH) {
                database.messageDao().clearMessageTimeline(accountKey = accountKey)
            }
            cursor = response.cursor
            Bluesky.saveDM(
                accountKey = accountKey,
                database = database,
                data = response.convos,
            )
            return MediatorResult.Success(
                endOfPaginationReached = response.cursor == null,
            )
        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }
}
