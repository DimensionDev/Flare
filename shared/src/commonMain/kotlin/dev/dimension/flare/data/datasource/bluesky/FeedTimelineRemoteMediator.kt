package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.GetFeedQueryParams
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import sh.christian.ozone.api.AtUri

@OptIn(ExperimentalPagingApi::class)
internal class FeedTimelineRemoteMediator(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
    private val uri: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey = "feed_timeline_$uri"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val service = getService()
        val response =
            when (request) {
                PagingRequest.Refresh ->
                    service
                        .getFeed(
                            GetFeedQueryParams(
                                feed = AtUri(atUri = uri),
                                limit = pageSize.toLong(),
                            ),
                        ).maybeResponse()

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service
                        .getFeed(
                            GetFeedQueryParams(
                                feed = AtUri(atUri = uri),
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).maybeResponse()
                }
            } ?: return PagingResult(
                endOfPaginationReached = true,
            )

        return PagingResult(
            endOfPaginationReached = response.cursor == null,
            data = response.feed.render(accountKey),
            nextKey = response.cursor,
        )
    }
}
