package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.GetFeedQueryParams
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import sh.christian.ozone.api.AtUri

@OptIn(ExperimentalPagingApi::class)
internal class FeedTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val uri: String,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "feed_timeline_$uri"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh ->
                    service
                        .getFeed(
                            GetFeedQueryParams(
                                feed = AtUri(atUri = uri),
                                limit = pageSize.toLong(),
                            ),
                        ).maybeResponse()

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service
                        .getFeed(
                            GetFeedQueryParams(
                                feed = AtUri(atUri = uri),
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).maybeResponse()
                }
            } ?: return Result(
                endOfPaginationReached = true,
            )

        return Result(
            endOfPaginationReached = response.cursor == null,
            data =
                response.feed.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.cursor,
        )
    }
}
