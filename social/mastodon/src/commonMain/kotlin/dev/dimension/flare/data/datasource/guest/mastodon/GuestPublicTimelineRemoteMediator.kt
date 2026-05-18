package dev.dimension.flare.data.datasource.guest.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

public class GuestPublicTimelineRemoteMediator(
    private val host: String,
    private val locale: String,
    private val local: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("mastodon_")
            if (local) {
                append("local_")
            } else {
                append("public_")
            }
            append(host)
        }

    private val service by lazy {
        GuestMastodonService("https://$host/", locale)
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.publicTimeline(
                        limit = pageSize,
                        local = if (local) true else null,
                    )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(endOfPaginationReached = true)
                }

                is PagingRequest.Append -> {
                    service.publicTimeline(
                        limit = pageSize,
                        max_id = request.nextKey,
                        local = if (local) true else null,
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data = response.map { it.render(host = host, accountKey = null) },
            nextKey = response.lastOrNull()?.id,
        )
    }
}
