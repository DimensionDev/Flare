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
import kotlinx.collections.immutable.toPersistentList
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

private fun List<GetPostThreadV2ThreadItem>.renderThread(accountKey: MicroBlogKey): List<UiTimelineV2> {
    val renderedPosts = mutableListOf<Pair<Long, UiTimelineV2.Post>>()
    val stack = mutableListOf<Pair<Long, UiTimelineV2.Post>>()

    mapNotNull { item ->
        when (val value = item.value) {
            is GetPostThreadV2ThreadItemValueUnion.Post -> item.depth to value.value.post
            else -> null
        }
    }.forEach { (depth, post) ->
        while (stack.lastOrNull()?.first?.let { it >= depth } == true) {
            stack.removeLast()
        }
        val parents =
            when {
                depth <= 0L -> stack.map { it.second }
                else -> stack.filter { it.first >= 0L }.map { it.second }
            }
        val current =
            post
                .render(accountKey)
                .copy(
                    parents = parents.toPersistentList(),
                )
        renderedPosts += depth to current
        stack += depth to current
    }

    val visiblePosts = renderedPosts.filter { it.first >= 0L }
    val descendantParentKeys =
        visiblePosts
            .filter { it.first > 0L }
            .map { it.second }
            .flatMap { it.collectParentKeys() }
            .toSet()

    return visiblePosts
        .filterNot { (depth, post) ->
            depth > 0L && post.statusKey in descendantParentKeys
        }.map { it.second }
}

private fun UiTimelineV2.Post.collectParentKeys(): Set<MicroBlogKey> =
    parents
        .map { it.statusKey }
        .toSet()
