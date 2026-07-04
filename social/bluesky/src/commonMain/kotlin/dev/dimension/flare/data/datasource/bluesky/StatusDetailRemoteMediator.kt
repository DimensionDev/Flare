package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetPostsQueryParams
import app.bsky.unspecced.GetPostThreadV2QueryParams
import app.bsky.unspecced.GetPostThreadV2Sort
import app.bsky.unspecced.GetPostThreadV2ThreadItem
import app.bsky.unspecced.GetPostThreadV2ThreadItemValueUnion
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.persistentListOf
import sh.christian.ozone.api.AtUri

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
    private val statusOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("status_detail_")
            if (statusOnly) {
                append("status_only_")
            }
            append(statusKey.toString())
            append("_")
            append(accountKey.toString())
        }
    override val collapseReplyChains: Boolean = false

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val service = getService()
        val result: List<UiTimelineV2> =
            when (request) {
                is PagingRequest.Append -> {
                    if (statusOnly) {
                        return PagingResult(
                            endOfPaginationReached = true,
                        )
                    } else {
                        val context =
                            service
                                .getPostThreadV2Unspecced(
                                    GetPostThreadV2QueryParams(
                                        AtUri(statusKey.id),
                                        branchingFactor = 1,
                                        below = 10,
                                        sort = GetPostThreadV2Sort.Top,
                                    ),
                                ).requireResponse()
                        context.thread.renderThread(accountKey)
                    }
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                PagingRequest.Refresh -> {
                    val current =
                        service
                            .getPosts(
                                GetPostsQueryParams(
                                    persistentListOf(AtUri(statusKey.id)),
                                ),
                            ).requireResponse()
                            .posts
                            .firstOrNull()
                    listOfNotNull(current).map(::FeedViewPost).render(accountKey)
                }
            }

        val shouldLoadMore = !(request is PagingRequest.Append || statusOnly)
        return PagingResult(
            endOfPaginationReached = !shouldLoadMore,
            data = result,
            nextKey = if (shouldLoadMore) pagingKey else null,
        )
    }
}

internal fun List<GetPostThreadV2ThreadItem>.renderThread(accountKey: MicroBlogKey): List<UiTimelineV2> =
    mapNotNull { item ->
        when (val value = item.value) {
            is GetPostThreadV2ThreadItemValueUnion.Post -> listOf(FeedViewPost(value.value.post)).render(accountKey).firstOrNull()
            else -> null
        }
    }
