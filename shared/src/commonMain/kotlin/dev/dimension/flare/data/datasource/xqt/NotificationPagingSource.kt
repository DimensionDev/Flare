package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.renderNotifications

internal class NotificationPagingSource(
    private val locale: String,
    private val service: XQTService,
    private val event: StatusEvent.XQT,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: () -> Unit,
) : BasePagingSource<String, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<String, UiTimeline>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiTimeline> {
        val response =
            service.getNotificationsAll(
                xTwitterClientLanguage = locale,
                cursor = params.key,
            )

        val topCursor = response.cursor(type = CursorType.top)
        if (topCursor != null) {
            service.postNotificationsAllLastSeenCursor(topCursor)
        }

        onClearMarker.invoke()

        val notifications = response.renderNotifications(accountKey, event)
        val cursor = response.cursor()

        return LoadResult.Page(
            data = notifications,
            prevKey = null,
            nextKey = cursor.takeIf { it != params.key },
        )
    }
}
