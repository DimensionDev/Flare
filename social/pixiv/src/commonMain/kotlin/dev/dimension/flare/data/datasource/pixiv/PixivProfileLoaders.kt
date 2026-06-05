package dev.dimension.flare.data.datasource.pixiv

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.pixiv.PixivService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile

internal class PixivSearchUserLoader(
    private val service: PixivService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.searchUsers(
                        word = query,
                    )
                }

                is PagingRequest.Append -> {
                    service.nextUsers(request.nextKey)
                }

                is PagingRequest.Prepend -> {
                    error("Handled above")
                }
            }

        return PagingResult(
            data = response.userPreviews.map { it.user.toUiProfile(accountKey) },
            nextKey = response.nextUrl,
            endOfPaginationReached = response.nextUrl == null,
        )
    }
}

internal class PixivDiscoverUserLoader(
    private val service: PixivService,
    private val accountKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.recommendedUsers()
                }

                is PagingRequest.Append -> {
                    service.nextUsers(request.nextKey)
                }

                is PagingRequest.Prepend -> {
                    error("Handled above")
                }
            }

        return PagingResult(
            data = response.userPreviews.map { it.user.toUiProfile(accountKey) },
            nextKey = response.nextUrl,
            endOfPaginationReached = response.nextUrl == null,
        )
    }
}

internal class PixivTrendHashtagLoader(
    private val service: PixivService,
) : RemoteLoader<UiHashtag> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiHashtag> {
        if (request != PagingRequest.Refresh) {
            return PagingResult(endOfPaginationReached = true)
        }

        val response = service.trendingTags()
        return PagingResult(
            data = response.trendTags.map { it.toUiHashtag() },
            endOfPaginationReached = true,
        )
    }
}
