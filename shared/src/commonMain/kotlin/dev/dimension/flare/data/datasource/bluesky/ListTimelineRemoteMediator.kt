package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import app.bsky.feed.GetListFeedQueryParams
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import sh.christian.ozone.api.AtUri

@OptIn(ExperimentalPagingApi::class)
internal class ListTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val uri: String,
    private val pagingKey: String,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    var cursor: String? = null

    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        val response =
            when (loadType) {
                LoadType.REFRESH ->
                    service
                        .getListFeed(
                            GetListFeedQueryParams(
                                list = AtUri(atUri = uri),
                                limit = state.config.pageSize.toLong(),
                            ),
                        ).maybeResponse()

                LoadType.PREPEND -> {
                    return MediatorResult.Success(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    service
                        .getListFeed(
                            GetListFeedQueryParams(
                                list = AtUri(atUri = uri),
                                limit = state.config.pageSize.toLong(),
                                cursor = cursor,
                            ),
                        ).maybeResponse()
                }
            } ?: return MediatorResult.Success(
                endOfPaginationReached = true,
            )

        cursor = response.cursor
        Bluesky.saveFeed(
            accountKey,
            pagingKey,
            database,
            response.feed,
        )

        return MediatorResult.Success(
            endOfPaginationReached = cursor == null,
        )
    }
}
