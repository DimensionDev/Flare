package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class LikePagingSource(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: suspend () -> Unit,
) : RemoteLoader<UiTimelineV2> {
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
                PagingRequest.Refresh -> 1
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
            }
        if (request == PagingRequest.Refresh) {
            onClearMarker()
        }

        val response = service.getAttitudes(page = page)
        val data =
            response.data
                .orEmpty()
                .filter { it.idStr != null }
                .map { it.render(accountKey) }
        return PagingResult(
            data = data,
            nextKey = if (data.isEmpty()) null else (page + 1).toString(),
        )
    }
}
