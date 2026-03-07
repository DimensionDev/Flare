package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class FansPagingSource(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    private val containerId by lazy {
        if (accountKey == userKey) {
            "231016_-_selffans"
        } else {
            "231051_-_fans_-_${userKey.id}"
        }
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val nextPage =
            when (request) {
                PagingRequest.Refresh -> 0
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 0
            }
        val users =
            service
                .getContainerIndex(containerId = containerId, sinceId = nextPage.toString())
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
            nextKey = if (users.isEmpty()) null else (users.size + nextPage).toString(),
        )
    }
}
