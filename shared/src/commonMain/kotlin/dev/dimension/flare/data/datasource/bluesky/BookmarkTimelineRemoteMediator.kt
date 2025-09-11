package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.bookmark.GetBookmarksQueryParams
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class BookmarkTimelineRemoteMediator(
    private val service: BlueskyService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "bookmark_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getBookmarks(
                            GetBookmarksQueryParams(
                                limit = pageSize.toLong(),
                            ),
                        ).requireResponse()
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service
                        .getBookmarks(
                            GetBookmarksQueryParams(
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).requireResponse()
                }
            }

        return Result(
            endOfPaginationReached = response.bookmarks.isEmpty() || response.cursor == null,
            data =
                response.bookmarks.toDb(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.cursor,
        )
    }
}
