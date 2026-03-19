package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.GetAuthorFeedFilter
import app.bsky.feed.GetAuthorFeedQueryParams
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import sh.christian.ozone.api.Did

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val onlyMedia: Boolean = false,
    private val withReplies: Boolean = false,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey =
        buildString {
            append("user_timeline")
            if (onlyMedia) {
                append("media")
            }
            if (withReplies) {
                append("replies")
            }
            append(accountKey.toString())
            append(userKey.toString())
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val service = getService()
        val filter =
            when {
                onlyMedia -> GetAuthorFeedFilter.PostsWithMedia
                withReplies -> GetAuthorFeedFilter.PostsWithReplies
                else -> GetAuthorFeedFilter.PostsAndAuthorThreads
            }
        val response =
            when (request) {
                PagingRequest.Refresh ->
                    service
                        .getAuthorFeed(
                            GetAuthorFeedQueryParams(
                                limit = pageSize.toLong(),
                                actor = Did(did = userKey.id),
                                filter = filter,
                                includePins = true,
                            ),
                        ).maybeResponse()

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service
                        .getAuthorFeed(
                            GetAuthorFeedQueryParams(
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                                actor = Did(did = userKey.id),
                                filter = filter,
                                includePins = true,
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
