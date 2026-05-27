package dev.dimension.flare.data.datasource.guest.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class GuestDiscoverStatusPagingSource(
    private val service: TrendsResources,
    private val host: String,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }
        val offset =
            when (request) {
                PagingRequest.Refresh -> 0
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 0
                is PagingRequest.Prepend -> 0
            }

        val result = service.trendsStatuses(limit = pageSize, offset = offset)
        return PagingResult(
            endOfPaginationReached = result.size < pageSize,
            data = result.map { it.render(host = host, accountKey = null) },
            nextKey = (offset + result.size).toString(),
        )
    }
}
