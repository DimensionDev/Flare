package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.renderNotifications
import dev.dimension.flare.ui.render.Render

internal class NotificationPagingSource(
    private val locale: String,
    private val service: XQTService,
    private val event: StatusEvent.XQT,
    private val accountKey: MicroBlogKey,
) : PagingSource<String, Render.Item>() {
    override fun getRefreshKey(state: PagingState<String, Render.Item>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Render.Item> =
        try {
            val response =
                service.getNotificationsAll(
                    xTwitterClientLanguage = locale,
                    cursor = params.key,
                )

            val notifications = response.renderNotifications(accountKey, event)
            val cursor = response.cursor()

            LoadResult.Page(
                data = notifications,
                prevKey = null,
                nextKey = cursor.takeIf { it != params.key },
            )
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
}
