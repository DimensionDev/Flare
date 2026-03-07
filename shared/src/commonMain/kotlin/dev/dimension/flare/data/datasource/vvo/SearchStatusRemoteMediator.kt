package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("search_")
            append(query)
            append(accountKey.toString())
        }

    private val containerId by lazy {
        "100103type=1&q=$query&t="
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                PagingRequest.Refresh -> 1
            }

        val response =
            service.getContainerIndex(
                containerId = containerId,
                pageType = "searchall",
                page = page.takeIf { request is PagingRequest.Append },
            )

        val status =
            response.data
                ?.cards
                ?.flatMap { card -> listOfNotNull(card.mblog) + card.cardGroup?.mapNotNull { it.mblog }.orEmpty() }
                .orEmpty()

        return PagingResult(
            endOfPaginationReached = status.isEmpty(),
            data = status.map { it.render(accountKey) },
            nextKey = (page + 1).toString(),
        )
    }
}
