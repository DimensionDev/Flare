package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.renderNotifications
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class NotificationPagingSource(
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
                PagingRequest.Refresh -> {
                    null
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    request.nextKey
                }
            }
        val instructions =
            service
                .getNotificationsTimeline(
                    variables =
                        NotificationsTimelineRequest(
                            timelineType = "All",
                            count = pageSize,
                            cursor = cursor,
                        ).encodeJson(),
                ).body()
                ?.data
                ?.viewerV2
                ?.userResults
                ?.result
                ?.notificationTimeline
                ?.timeline
                ?.instructions
                .orEmpty()

        val topCursor = instructions.cursor(type = CursorType.top)
        if (topCursor != null) {
            service.postNotificationsAllLastSeenCursor(topCursor)
        }

        if (request == PagingRequest.Refresh) {
            onClearMarker.invoke()
        }

        val notifications = instructions.renderNotifications(accountKey)
        val nextCursor = instructions.cursor()

        return PagingResult(
            data = notifications,
            nextKey = nextCursor.takeIf { it != cursor },
        )
    }
}

@Serializable
internal data class NotificationsTimelineRequest(
    @SerialName("timeline_type")
    val timelineType: String,
    val count: Int,
    val cursor: String? = null,
)
