package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.SearchPostsQueryParams
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("search_")
            append(query)
            append(accountKey.toString())
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val service = getService()
        val response =
            when (request) {
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                PagingRequest.Refresh -> {
                    service.searchPosts(
                        SearchPostsQueryParams(
                            q = query,
                            limit = pageSize.toLong(),
                        ),
                    )
                }

                is PagingRequest.Append -> {
                    service.searchPosts(
                        SearchPostsQueryParams(
                            q = query,
                            limit = pageSize.toLong(),
                            cursor = request.nextKey,
                        ),
                    )
                }
            }.requireResponse()

        return PagingResult(
            endOfPaginationReached = response.cursor == null,
            data = response.posts.map { it.render(accountKey) },
            nextKey = response.cursor,
        )
    }
}
