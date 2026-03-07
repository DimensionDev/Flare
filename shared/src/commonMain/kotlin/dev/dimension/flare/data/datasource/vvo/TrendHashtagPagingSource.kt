package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val accountKey: MicroBlogKey,
    private val service: VVOService,
) : RemoteLoader<UiHashtag> {
    private val containerId = "106003type=25&filter_type=realtimehot"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiHashtag> {
        if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
            return PagingResult(
                endOfPaginationReached = true,
            )
        }

        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val data =
            service
                .getContainerIndex(containerId = containerId)
                .data
                ?.cards
                ?.flatMap {
                    it.cardGroup.orEmpty()
                }?.mapNotNull {
                    it.desc
                }?.map {
                    UiHashtag(
                        hashtag = it,
                        description = null,
                        searchContent = "#$it#",
                    )
                }?.toList()
                ?.take(10)
                .orEmpty()

        return PagingResult(
            data = data,
            endOfPaginationReached = true,
        )
    }
}
