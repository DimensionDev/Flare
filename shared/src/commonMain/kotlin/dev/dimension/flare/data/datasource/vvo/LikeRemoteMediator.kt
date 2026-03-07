package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class LikeRemoteMediator(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "like_$accountKey"
    private val containerId = "2308691748186704_-_mix"

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
                PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                is PagingRequest.Append -> request.nextKey.toIntOrNull()
            }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getContainerIndex(containerId = containerId)
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.getContainerIndex(
                        containerId = containerId,
                        page = page,
                        openApp = 0,
                    )
                }
            }

        val status =
            response.data
                ?.cards
                ?.flatMap {
                    listOfNotNull(
                        it.mblog,
                    ) +
                        it.cardGroup
                            ?.mapNotNull {
                                it.mblog
                            }.orEmpty()
                }?.filter { it.user?.id != null }
                .orEmpty()

        val nextKey = response.data?.cardlistInfo?.page
        return PagingResult(
            endOfPaginationReached = nextKey == null,
            data = status.map { it.render(accountKey) },
            nextKey = nextKey?.toString(),
        )
    }
}
