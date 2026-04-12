package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class MentionRemoteMediator(
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "mention_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .notification(
                            limit = pageSize,
                            exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
                        )
                }

                is PagingRequest.Prepend -> {
                    service.notification(
                        limit = pageSize,
                        min_id = request.previousKey,
                        exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
                    )
                }

                is PagingRequest.Append -> {
                    service.notification(
                        limit = pageSize,
                        max_id = request.nextKey,
                        exclude_types = NotificationTypes.entries.filter { it != NotificationTypes.Mention },
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data = response.map { it.render(accountKey) },
            nextKey = response.next,
            previousKey = response.prev,
        )
    }
}
