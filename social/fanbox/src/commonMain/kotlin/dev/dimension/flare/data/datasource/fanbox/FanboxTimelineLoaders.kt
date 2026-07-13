package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.fanbox.FanboxCommentItem
import dev.dimension.flare.data.network.fanbox.FanboxCommentListResponse
import dev.dimension.flare.data.network.fanbox.FanboxPostPage
import dev.dimension.flare.data.network.fanbox.FanboxService
import dev.dimension.flare.data.network.fanbox.toFanboxCursor
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import io.ktor.http.Url
import kotlinx.collections.immutable.toImmutableList

internal abstract class FanboxTimelineLoader(
    protected val service: FanboxService,
    protected val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val response =
            when (request) {
                PagingRequest.Refresh -> loadPage(pageSize, null)
                is PagingRequest.Append -> loadPage(pageSize, request.nextKey)
                is PagingRequest.Prepend -> error("Handled above")
            }

        val imageHeaders = service.fanboxImageHeaders()
        return PagingResult(
            data =
                response
                    .items
                    .map {
                        it.toUiTimeline(
                            accountKey = accountKey,
                            imageHeaders = imageHeaders,
                        )
                    },
            nextKey = response.nextKey,
            endOfPaginationReached = response.nextKey == null,
        )
    }

    protected abstract suspend fun loadPage(
        pageSize: Int,
        nextKey: String?,
    ): FanboxPostPage
}

internal class FanboxHomeTimelineLoader(
    service: FanboxService,
    accountKey: MicroBlogKey,
) : FanboxTimelineLoader(service, accountKey) {
    override val pagingKey: String = "fanbox_home_$accountKey"

    override suspend fun loadPage(
        pageSize: Int,
        nextKey: String?,
    ): FanboxPostPage {
        val cursor = nextKey?.toFanboxCursor()
        val response =
            service.listHomePosts(
                limit = (cursor?.limit ?: pageSize).coerceAtLeast(1),
                firstPublishedDatetime = cursor?.firstPublishedDatetime,
                maxPublishedDatetime = cursor?.maxPublishedDatetime,
                firstId = cursor?.firstId,
                maxId = cursor?.maxId,
            )
        return FanboxPostPage(
            items = response.body.items,
            nextKey = response.body.nextUrl,
        )
    }
}

internal class FanboxSupportedTimelineLoader(
    service: FanboxService,
    accountKey: MicroBlogKey,
) : FanboxTimelineLoader(service, accountKey) {
    override val pagingKey: String = "fanbox_supported_$accountKey"

    override suspend fun loadPage(
        pageSize: Int,
        nextKey: String?,
    ): FanboxPostPage {
        val cursor = nextKey?.toFanboxCursor()
        val response =
            service.listSupportingPosts(
                limit = (cursor?.limit ?: pageSize).coerceAtLeast(1),
                firstPublishedDatetime = cursor?.firstPublishedDatetime,
                maxPublishedDatetime = cursor?.maxPublishedDatetime,
                firstId = cursor?.firstId,
                maxId = cursor?.maxId,
            )
        return FanboxPostPage(
            items = response.body.items,
            nextKey = response.body.nextUrl,
        )
    }
}

internal class FanboxCreatorTimelineLoader(
    service: FanboxService,
    accountKey: MicroBlogKey,
    private val creatorKey: MicroBlogKey,
) : FanboxTimelineLoader(service, accountKey) {
    override val pagingKey: String = "fanbox_creator_${creatorKey.id}_$accountKey"

    override suspend fun loadPage(
        pageSize: Int,
        nextKey: String?,
    ): FanboxPostPage {
        val creatorId = creatorKey.resolveCreatorId() ?: return FanboxPostPage(emptyList(), null)
        val pages = service.paginateCreatorPosts(creatorId = creatorId).body
        val currentPage = nextKey ?: pages.firstOrNull()
        val nextPage =
            currentPage
                ?.let { page -> pages.indexOf(page).takeIf { it >= 0 } }
                ?.let { index -> pages.getOrNull(index + 1) }

        if (currentPage == null) {
            return FanboxPostPage(emptyList(), null)
        }

        val cursor = currentPage.toFanboxCursor()
        val response =
            service.listCreatorPosts(
                creatorId = creatorId,
                limit = (cursor.limit ?: pageSize).coerceAtLeast(1),
                firstPublishedDatetime = cursor.firstPublishedDatetime,
                maxPublishedDatetime = cursor.maxPublishedDatetime,
                firstId = cursor.firstId,
                maxId = cursor.maxId,
            )
        return FanboxPostPage(
            items = response.body,
            nextKey = nextPage,
        )
    }

    private suspend fun MicroBlogKey.resolveCreatorId(): String? =
        if (this == accountKey) {
            service
                .credentialWithCsrf()
                .creatorId
                ?.takeIf { it.isNotBlank() }
        } else {
            id
        }
}

internal class FanboxSearchTimelineLoader(
    service: FanboxService,
    accountKey: MicroBlogKey,
    private val query: String,
) : FanboxTimelineLoader(service, accountKey) {
    override val pagingKey: String = "fanbox_search_${query}_$accountKey"

    override suspend fun loadPage(
        pageSize: Int,
        nextKey: String?,
    ): FanboxPostPage {
        val page = nextKey?.toIntOrNull() ?: 0
        val response =
            service.listTaggedPosts(
                tag = query.trimStart('#'),
                page = page,
            )
        return FanboxPostPage(
            items = response.body.items,
            nextKey =
                response.body.nextUrl?.let { url ->
                    Url(url).parameters["page"]
                },
        )
    }
}

internal class FanboxStatusDetailLoader(
    private val service: FanboxService,
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "fanbox_status_${statusKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request != PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }
        val imageHeaders = service.fanboxImageHeaders()
        return PagingResult(
            data =
                listOf(
                    service
                        .postInfo(postId = statusKey.id)
                        .body
                        .toUiTimeline(
                            accountKey = accountKey,
                            imageHeaders = imageHeaders,
                        ),
                ),
            endOfPaginationReached = true,
        )
    }
}

internal class FanboxCommentsLoader(
    private val service: FanboxService,
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
    private val fetchComments: suspend (postId: String, offset: Int, limit: Int) -> FanboxCommentListResponse =
        service::getComments,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "fanbox_comments_${statusKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val (comments, nextKey) =
            when (request) {
                PagingRequest.Refresh -> loadCommentPage(pageSize = pageSize, nextKey = null)
                is PagingRequest.Append -> loadCommentPage(pageSize = pageSize, nextKey = request.nextKey)
                is PagingRequest.Prepend -> error("Handled above")
            }

        val imageHeaders = service.fanboxImageHeaders()
        return PagingResult(
            data =
                comments.map { comment ->
                    val post =
                        comment.toUiTimeline(
                            accountKey = accountKey,
                            postKey = statusKey,
                            imageHeaders = imageHeaders,
                        )
                    val replies =
                        comment.replies
                            .map { reply ->
                                reply.toUiTimeline(
                                    accountKey = accountKey,
                                    postKey = statusKey,
                                    imageHeaders = imageHeaders,
                                )
                            }.toImmutableList()
                    if (replies.isNotEmpty()) {
                        UiTimelineV2.TimelinePostItem(
                            post = post,
                            presentation =
                                UiTimelineV2.PostPresentation(
                                    quotes = replies,
                                ),
                        )
                    } else {
                        post
                    }
                },
            nextKey = nextKey,
            endOfPaginationReached = nextKey == null,
        )
    }

    private suspend fun loadCommentPage(
        pageSize: Int,
        nextKey: String?,
    ): Pair<List<FanboxCommentItem>, String?> {
        val offset = nextKey?.toIntOrNull() ?: 0
        val response =
            fetchComments(
                statusKey.id,
                offset,
                pageSize.coerceAtLeast(1),
            )
        val nextOffset =
            response.body.commentList.nextUrl?.let { url ->
                Url(url).parameters["offset"]
            }
        val comments = response.body.commentList.items
        return comments to nextOffset
    }
}

internal class FanboxRecommendedCreatorsTimelineLoader(
    private val service: FanboxService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "fanbox_recommended_creators_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request != PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }
        val imageHeaders = service.fanboxImageHeaders()
        return PagingResult(
            data =
                service
                    .listRecommendedCreators(limit = pageSize.coerceAtLeast(1))
                    .body
                    .creators
                    .map {
                        it.toUiTimeline(
                            accountKey = accountKey,
                            imageHeaders = imageHeaders,
                        )
                    },
            endOfPaginationReached = true,
        )
    }
}
