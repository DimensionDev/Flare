package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.INotificationsRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class NotificationRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey = "notification_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                is PagingRequest.Prepend -> return PagingResult(
                    endOfPaginationReached = true,
                )
                PagingRequest.Refresh -> {
                    service.iNotifications(
                        INotificationsRequest(
                            limit = pageSize,
                        ),
                    )
                }

                is PagingRequest.Append -> {
                    service.iNotifications(
                        INotificationsRequest(
                            limit = pageSize,
                            untilId = request.nextKey,
                        ),
                    )
                }
            }

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.render(accountKey),
            nextKey = response.lastOrNull()?.id,
        )
    }
}
