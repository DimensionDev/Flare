package dev.dimension.flare.data.datasource.pixiv

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.pixiv.PixivRankingMode
import dev.dimension.flare.data.network.pixiv.PixivSearchSort
import dev.dimension.flare.data.network.pixiv.PixivService
import dev.dimension.flare.data.network.pixiv.PixivWorkType
import dev.dimension.flare.data.network.pixiv.model.PixivCommentListResponse
import dev.dimension.flare.data.network.pixiv.model.PixivIllustListResponse
import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal abstract class PixivTimelineLoader(
    protected val service: PixivService,
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
                PagingRequest.Refresh -> loadFirstPage(pageSize)
                is PagingRequest.Append -> loadNextPage(request.nextKey)
                is PagingRequest.Prepend -> error("Handled above")
            }

        return PagingResult(
            data = response.illusts.map { it.toUiTimeline(accountKey) },
            nextKey = response.nextUrl,
            endOfPaginationReached = response.nextUrl == null,
        )
    }

    protected abstract suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse

    private suspend fun loadNextPage(nextUrl: String?): PixivIllustListResponse =
        if (nextUrl == null) {
            PixivIllustListResponse()
        } else {
            service.nextIllusts(nextUrl)
        }
}

internal class PixivHomeTimelineLoader(
    service: PixivService,
    accountKey: MicroBlogKey,
) : PixivTimelineLoader(service, accountKey) {
    override val pagingKey: String = "pixiv_home_$accountKey"

    override suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse = service.followedIllusts()
}

internal class PixivDiscoverTimelineLoader(
    service: PixivService,
    accountKey: MicroBlogKey,
) : PixivTimelineLoader(service, accountKey) {
    override val pagingKey: String = "pixiv_discover_$accountKey"

    override suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse =
        service.rankingIllusts(
            mode = PixivRankingMode.Day,
        )
}

internal class PixivRankingTimelineLoader(
    service: PixivService,
    accountKey: MicroBlogKey,
    private val mode: PixivRankingMode,
) : PixivTimelineLoader(service, accountKey) {
    override val pagingKey: String = "pixiv_ranking_${mode.value}_$accountKey"

    override suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse =
        service.rankingIllusts(
            mode = mode,
        )
}

internal class PixivFollowingTimelineLoader(
    service: PixivService,
    accountKey: MicroBlogKey,
) : PixivTimelineLoader(service, accountKey) {
    override val pagingKey: String = "pixiv_following_$accountKey"

    override suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse = service.followedIllusts()
}

internal class PixivSearchTimelineLoader(
    service: PixivService,
    accountKey: MicroBlogKey,
    private val query: String,
) : PixivTimelineLoader(service, accountKey) {
    override val pagingKey: String = "pixiv_search_${query}_$accountKey"

    override suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse =
        service.searchIllusts(
            word = query,
            sort = PixivSearchSort.DateDesc,
        )
}

internal class PixivUserTimelineLoader(
    service: PixivService,
    accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val type: PixivWorkType = PixivWorkType.Illust,
) : PixivTimelineLoader(service, accountKey) {
    override val pagingKey: String = "pixiv_user_${type.value}_${userKey}_$accountKey"

    override suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse {
        val userId = userKey.id.toLongOrNull() ?: return PixivIllustListResponse()
        return service.userIllusts(
            userId = userId,
            type = type,
        )
    }
}

internal class PixivStatusDetailLoader(
    private val service: PixivService,
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "pixiv_status_${statusKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request != PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }

        val illustId = statusKey.id.toLongOrNull() ?: return PagingResult(endOfPaginationReached = true)
        val response =
            service.illustDetail(
                illustId = illustId,
            )

        return PagingResult(
            data = listOf(response.illust.toUiTimeline(accountKey)),
            endOfPaginationReached = true,
        )
    }
}

internal class PixivGalleryCommentsLoader(
    private val service: PixivService,
    private val accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "pixiv_gallery_comments_${statusKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val response =
            when (request) {
                PagingRequest.Refresh -> loadFirstPage()
                is PagingRequest.Append -> loadNextPage(request.nextKey)
                is PagingRequest.Prepend -> error("Handled above")
            }

        return PagingResult(
            data = response.comments.map { it.toUiTimeline(accountKey, statusKey) },
            nextKey = response.nextUrl,
            endOfPaginationReached = response.nextUrl == null,
        )
    }

    private suspend fun loadFirstPage(): PixivCommentListResponse {
        val illustId = statusKey.id.toLongOrNull() ?: return PixivCommentListResponse()
        return service.illustComments(
            illustId = illustId,
        )
    }

    private suspend fun loadNextPage(nextUrl: String?): PixivCommentListResponse =
        if (nextUrl == null) {
            PixivCommentListResponse()
        } else {
            service.nextComments(nextUrl)
        }
}

internal class PixivGalleryRecommendationsLoader(
    service: PixivService,
    accountKey: MicroBlogKey,
    private val statusKey: MicroBlogKey,
) : PixivTimelineLoader(service, accountKey) {
    override val pagingKey: String = "pixiv_gallery_recommendations_${statusKey}_$accountKey"

    override suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse {
        val illustId = statusKey.id.toLongOrNull() ?: return PixivIllustListResponse()
        return service.relatedIllusts(
            illustId = illustId,
        )
    }
}

internal class PixivBookmarkTimelineLoader(
    service: PixivService,
    private val credentialFlow: Flow<PixivCredential>,
    accountKey: MicroBlogKey,
) : PixivTimelineLoader(service, accountKey) {
    override val pagingKey: String = "pixiv_bookmark_$accountKey"

    override suspend fun loadFirstPage(pageSize: Int): PixivIllustListResponse {
        val credential = credentialFlow.first()
        return service.userBookmarkedIllusts(
            userId = credential.userId,
        )
    }
}
