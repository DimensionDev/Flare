package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.SearchPostsQueryParams
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: BlueskyService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String =
        buildString {
            append("search_")
            append(query)
            append(accountKey.toString())
        }

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }
                Request.Refresh -> {
                    service.searchPosts(
                        SearchPostsQueryParams(
                            q = query,
                            limit = pageSize.toLong(),
                        ),
                    )
                }

                is Request.Append -> {
                    service.searchPosts(
                        SearchPostsQueryParams(
                            q = query,
                            limit = pageSize.toLong(),
                            cursor = request.nextKey,
                        ),
                    )
                }
            }.requireResponse()

        return Result(
            endOfPaginationReached = response.cursor == null,
            data =
                response.posts.toDb(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.cursor,
        )
    }
}
