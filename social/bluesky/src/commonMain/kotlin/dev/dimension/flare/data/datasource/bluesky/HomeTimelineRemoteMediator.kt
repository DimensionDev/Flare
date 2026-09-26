package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.GetTimelineResponse
import app.bsky.feed.ReplyRefParentUnion
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "home_$accountKey"

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
                    service
                        .getTimeline(
                            GetTimelineQueryParams(
                                limit = pageSize.toLong(),
                            ),
                        ).maybeResponse()
                }

                is PagingRequest.Append -> {
                    service
                        .getTimeline(
                            GetTimelineQueryParams(
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).maybeResponse()
                }
            } ?: return PagingResult(
                endOfPaginationReached = true,
            )
        return response.renderHomeTimeline(accountKey)
    }
}

internal suspend fun GetTimelineResponse.renderHomeTimeline(accountKey: MicroBlogKey): PagingResult<UiTimelineV2> =
    PagingResult(
        endOfPaginationReached = cursor == null,
        data = feed.filter { it.isVisibleInHomeTimeline(accountKey) }.renderWithDownloadUrls(accountKey),
        nextKey = cursor,
    )

private fun FeedViewPost.isVisibleInHomeTimeline(accountKey: MicroBlogKey): Boolean {
    val parent = (reply?.parent as? ReplyRefParentUnion.PostView)?.value ?: return true
    // Keep self-threads and replies to the viewer, who cannot follow their own account.
    if (parent.author.did == post.author.did || parent.author.did.did == accountKey.id) return true
    val viewer = parent.author.viewer ?: return true
    return viewer.following != null
}
