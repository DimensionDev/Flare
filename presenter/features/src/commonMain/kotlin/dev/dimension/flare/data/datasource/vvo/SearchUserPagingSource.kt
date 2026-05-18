package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : RemoteLoader<UiProfile> {
    private val containerId by lazy {
        "100103type=3&q=$query&t="
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                PagingRequest.Refresh -> {
                    null
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    request.nextKey.toIntOrNull()
                }
            }

        val response =
            service.getContainerIndex(
                containerId = containerId,
                pageType = "searchall",
                page = page,
            )
        val users =
            response.data
                ?.cards
                ?.flatMap {
                    it.cardGroup.orEmpty()
                }?.mapNotNull {
                    it.user
                }.orEmpty()

        return PagingResult(
            data = users.map { it.render(accountKey = accountKey) },
            nextKey = if (users.isEmpty()) null else ((page ?: 0) + 1).toString(),
        )
    }
}
