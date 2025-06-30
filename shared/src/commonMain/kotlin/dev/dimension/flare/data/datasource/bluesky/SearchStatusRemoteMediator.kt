package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import app.bsky.feed.SearchPostsQueryParams
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: BlueskyService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BaseTimelineRemoteMediator(
        database = database,
        accountType = AccountType.Specific(accountKey),
    ) {
    var cursor: String? = null

    override val pagingKey: String =
        buildString {
            append("search_")
            append(query)
            append(accountKey.toString())
        }

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }
                LoadType.REFRESH -> {
                    service.searchPosts(
                        SearchPostsQueryParams(
                            q = query,
                            limit = state.config.pageSize.toLong(),
                        ),
                    )
                }

                LoadType.APPEND -> {
                    service.searchPosts(
                        SearchPostsQueryParams(
                            q = query,
                            limit = state.config.pageSize.toLong(),
                            cursor = cursor,
                        ),
                    )
                }
            }.requireResponse()
        cursor = response.cursor

        return Result(
            endOfPaginationReached = cursor == null,
            data =
                response.posts.toDb(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
        )
    }
}
