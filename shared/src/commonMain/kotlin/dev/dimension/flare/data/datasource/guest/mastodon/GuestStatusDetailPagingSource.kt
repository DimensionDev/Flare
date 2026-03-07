package dev.dimension.flare.data.datasource.guest.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class GuestStatusDetailPagingSource(
    private val service: GuestMastodonService,
    private val host: String,
    private val statusKey: MicroBlogKey,
    private val statusOnly: Boolean,
) : RemoteLoader<UiTimelineV2> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
            return PagingResult(endOfPaginationReached = true)
        }

        val result =
            if (statusOnly) {
                listOf(service.lookupStatus(statusKey.id))
            } else {
                val context = service.context(statusKey.id)
                val current = service.lookupStatus(statusKey.id)
                context.ancestors.orEmpty() + listOf(current) + context.descendants.orEmpty()
            }

        return PagingResult(
            endOfPaginationReached = true,
            data = result.map { it.render(host = host, accountKey = null) },
        )
    }
}
