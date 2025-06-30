package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import app.bsky.feed.GetFeedQueryParams
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import sh.christian.ozone.api.AtUri

@OptIn(ExperimentalPagingApi::class)
internal class FeedTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val uri: String,
    private val pagingKey: String,
) : BaseTimelineRemoteMediator(
        database = database,
        clearWhenRefresh = false,
        pagingKey = pagingKey,
        accountType = AccountType.Specific(accountKey),
    ) {
    var cursor: String? = null

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH ->
                    service
                        .getFeed(
                            GetFeedQueryParams(
                                feed = AtUri(atUri = uri),
                                limit = state.config.pageSize.toLong(),
                            ),
                        ).maybeResponse()
                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }
                LoadType.APPEND -> {
                    service
                        .getFeed(
                            GetFeedQueryParams(
                                feed = AtUri(atUri = uri),
                                limit = state.config.pageSize.toLong(),
                                cursor = cursor,
                            ),
                        ).maybeResponse()
                }
            } ?: return Result(
                endOfPaginationReached = true,
            )
        cursor = response.cursor

        return Result(
            endOfPaginationReached = cursor == null,
            data =
                Bluesky.saveFeed(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    data = response.feed,
                ),
        )
    }
}
