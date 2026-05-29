package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.bookmark.GetBookmarksQueryParams
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class BookmarkTimelineRemoteMediator(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bookmark_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val service = getService()
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .getBookmarks(
                            GetBookmarksQueryParams(
                                limit = pageSize.toLong(),
                            ),
                        ).requireResponse()
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service
                        .getBookmarks(
                            GetBookmarksQueryParams(
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).requireResponse()
                }
            }

        return PagingResult(
            endOfPaginationReached = response.bookmarks.isEmpty() || response.cursor == null,
            data = response.bookmarks.mapNotNull { it.render(accountKey) },
            nextKey = response.cursor,
        )
    }
}
