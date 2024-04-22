package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.notifications
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStatus

internal class NotificationPagingSource(
    private val locale: String,
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : PagingSource<String, UiStatus>() {
    override fun getRefreshKey(state: PagingState<String, UiStatus>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiStatus> {
        return try {
            val response =
                service.getNotificationsAll(
                    xTwitterClientLanguage = locale,
                    cursor = params.key,
                )

            val notifications = response.notifications(accountKey)
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
}
