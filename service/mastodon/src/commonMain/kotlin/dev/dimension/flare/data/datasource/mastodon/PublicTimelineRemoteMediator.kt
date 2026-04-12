package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class PublicTimelineRemoteMediator(
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
    private val local: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("public_timeline")
            if (local) {
                append("_local")
            }
            append("_$accountKey")
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .publicTimeline(
                            limit = pageSize,
                            local = local,
                        )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.publicTimeline(
                        limit = pageSize,
                        max_id = request.nextKey,
                        local = local,
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data = response.render(accountKey),
            nextKey = response.next,
            previousKey = response.prev,
        )
    }
}
