package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class DiscoverStatusRemoteMediator(
    private val service: TrendsResources,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "discover_status_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val offset =
            when (request) {
                PagingRequest.Refresh -> {
                    0
                }

                is PagingRequest.Append -> {
                    request.nextKey.toIntOrNull() ?: 0
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
            }

        val response = service.trendsStatuses(limit = pageSize, offset = offset)
        return PagingResult(
            endOfPaginationReached = response.size < pageSize,
            data = response.render(accountKey),
            nextKey = (offset + response.size).toString(),
        )
    }
}
