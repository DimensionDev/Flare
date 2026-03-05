package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.renderNotifications

internal class NotificationPagingSource(
    private val locale: String,
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: () -> Unit,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "notification_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val cursor =
            when (request) {
                PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                is PagingRequest.Append -> request.nextKey
            }
        val response =
            service.getNotificationsAll(
                xTwitterClientLanguage = locale,
                cursor = cursor,
            )

        val topCursor = response.cursor(type = CursorType.top)
        if (topCursor != null) {
            service.postNotificationsAllLastSeenCursor(topCursor)
        }

        if (request == PagingRequest.Refresh) {
            onClearMarker.invoke()
        }

        val notifications = response.renderNotifications(accountKey)
        val nextCursor = response.cursor()

        return PagingResult(
            data = notifications,
            nextKey = nextCursor.takeIf { it != cursor },
        )
    }
}
