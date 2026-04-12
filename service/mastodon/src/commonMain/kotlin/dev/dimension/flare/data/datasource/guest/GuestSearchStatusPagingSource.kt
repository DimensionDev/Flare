package dev.dimension.flare.data.datasource.guest.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class GuestSearchStatusPagingSource(
    private val service: GuestMastodonService,
    private val host: String,
    private val query: String,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val maxId = (request as? PagingRequest.Append)?.nextKey
        val result =
            if (query.startsWith("#")) {
                service.hashtagTimeline(
                    hashtag = query.removePrefix("#"),
                    limit = pageSize,
                    max_id = maxId,
                )
            } else {
                service
                    .searchV2(
                        query = query,
                        limit = pageSize,
                        type = "statuses",
                        max_id = maxId,
                    ).statuses
            }

        val data = result.orEmpty()
        return PagingResult(
            endOfPaginationReached = data.isEmpty(),
            data = data.map { it.render(host = host, accountKey = null) },
            nextKey = data.lastOrNull()?.id,
        )
    }
}
