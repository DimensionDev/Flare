package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datasource.microblog.paging.NotificationTimelineLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.ReportableRemoteLoader
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

internal class FollowRequestsNotificationLoader(
    private val notifications: NotificationTimelineLoader,
    private val followRequests: RemoteLoader<UiTimelineV2>,
) : NotificationTimelineLoader,
    ReportableRemoteLoader {
    override val pagingKey: String = notifications.pagingKey
    override var reportError: ((Throwable) -> Unit)? = null

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val cursor =
            when (request) {
                PagingRequest.Refresh -> {
                    Cursor(null, null)
                }

                is PagingRequest.Append -> {
                    if (request.nextKey.startsWith(CURSOR_PREFIX)) {
                        request.nextKey.removePrefix(CURSOR_PREFIX).decodeJson<Cursor>()
                    } else {
                        // Cached notification cursors from before follow requests were included.
                        Cursor(notifications = request.nextKey, followRequests = null)
                    }
                }

                is PagingRequest.Prepend -> {
                    return PagingResult()
                }
            }
        val notificationPage =
            if (request == PagingRequest.Refresh) {
                notifications.load(pageSize, PagingRequest.Refresh)
            } else {
                cursor.notifications?.let { notifications.load(pageSize, PagingRequest.Append(it)) } ?: PagingResult()
            }
        // shortcut: Preserve generic notices until a captured follow-request payload provides a reliable discriminator.
        val requestPage =
            try {
                if (request == PagingRequest.Refresh) {
                    followRequests.load(pageSize, PagingRequest.Refresh)
                } else {
                    cursor.followRequests?.let { followRequests.load(pageSize, PagingRequest.Append(it)) } ?: PagingResult()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val report = reportError ?: throw error
                report(error)
                PagingResult()
            }
        val next = Cursor(notificationPage.nextKey, requestPage.nextKey)
        return PagingResult(
            data = requestPage.data + notificationPage.data,
            nextKey =
                if (next.notifications != null || next.followRequests != null) {
                    CURSOR_PREFIX + next.encodeJson()
                } else {
                    null
                },
        )
    }

    @Serializable
    private data class Cursor(
        val notifications: String?,
        val followRequests: String?,
    )

    private companion object {
        const val CURSOR_PREFIX = "xqt-notifications:"
    }
}
