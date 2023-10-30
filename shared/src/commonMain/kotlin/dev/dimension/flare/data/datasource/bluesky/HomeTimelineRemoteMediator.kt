package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.cash.paging.LoadType
import app.cash.paging.PagingState
import app.cash.paging.RemoteMediator
import app.bsky.feed.GetTimelineQueryParams
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {

    var cursor: String? = null
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
            val response = when (loadType) {
                LoadType.PREPEND -> return MediatorResult.Success(
                    endOfPaginationReached = true,
                )

                LoadType.REFRESH -> {
                    service.getTimeline(
                        GetTimelineQueryParams(
                            algorithm = "reverse-chronological",
                            limit = state.config.pageSize.toLong(),
                        ),
                    ).maybeResponse()
                }

                LoadType.APPEND -> {
                    service.getTimeline(
                        GetTimelineQueryParams(
                            algorithm = "reverse-chronological",
                            limit = state.config.pageSize.toLong(),
                            cursor = cursor,
                        ),
                    ).maybeResponse()
                }
            } ?: return MediatorResult.Success(
                endOfPaginationReached = true,
            )
            if (loadType == LoadType.REFRESH) {
                database.dbPagingTimelineQueries.deletePaging(accountKey, pagingKey)
            }
            cursor = response.cursor
            Bluesky.saveFeed(
                accountKey,
                pagingKey,
                database,
                response.feed,
            )

            MediatorResult.Success(
                endOfPaginationReached = response.feed.isEmpty(),
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
