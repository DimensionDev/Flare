package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class FollowingPagingSource(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    private val containerId by lazy {
        if (accountKey == userKey) {
            "231093_-_selffollowed"
        } else {
            "231051_-_followers_-_${userKey.id}"
        }
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val nextPage =
            when (request) {
                PagingRequest.Refresh -> 1
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
            }
        val users =
            service
                .getContainerIndex(containerId = containerId, page = nextPage)
                .data
                ?.cards
                ?.filter { it.cardType == 11L && it.cardStyle == null }
                ?.mapNotNull {
                    it.cardGroup
                }?.flatMap { it }
                ?.mapNotNull { it.user }
                ?.map {
                    it.render(accountKey = accountKey)
                }.orEmpty()
        return PagingResult(
            data = users,
            nextKey = if (users.isEmpty()) null else (nextPage + 1).toString(),
        )
    }
}
