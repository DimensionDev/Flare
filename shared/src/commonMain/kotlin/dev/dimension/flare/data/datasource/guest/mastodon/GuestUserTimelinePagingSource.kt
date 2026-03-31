package dev.dimension.flare.data.datasource.guest.mastodon

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.api.TimelineResources
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class GuestUserTimelinePagingSource(
    private val service: TimelineResources,
    private val host: String,
    private val userId: String,
    private val withReply: Boolean = false,
    private val onlyMedia: Boolean = false,
    private val withPinned: Boolean = false,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String
        get() =
            buildString {
                append("mastodon_guest")
                append("_user_timeline")
                append("_$host")
                append("_$userId")
                append("_$withReply")
                append("_$onlyMedia")
                append("_$withPinned")
            }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val maxId = (request as? PagingRequest.Append)?.nextKey
        if (request is PagingRequest.Prepend) {
            return PagingResult(endOfPaginationReached = true)
        }

        val pinned =
            if (withPinned && request == PagingRequest.Refresh) {
                service.userTimeline(
                    user_id = userId,
                    pinned = true,
                )
            } else {
                emptyList()
            }

        val statuses =
            service
                .userTimeline(
                    user_id = userId,
                    limit = pageSize,
                    max_id = maxId,
                    only_media = onlyMedia,
                    exclude_replies = !withReply,
                    pinned = false,
                ).let {
                    if (withPinned) {
                        pinned + it
                    } else {
                        it
                    }
                }.distinctBy { it.id }

        return PagingResult(
            endOfPaginationReached = statuses.isEmpty(),
            data = statuses.map { it.render(host = host, accountKey = null) },
            nextKey = statuses.lastOrNull()?.id,
        )
    }
}
