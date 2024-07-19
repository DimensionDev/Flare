package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.render.Render

internal class GuestUserTimelinePagingSource(
    private val userId: String,
    private val event: StatusEvent.Mastodon,
    private val onlyMedia: Boolean = false,
) : PagingSource<String, Render.Item>() {
    override fun getRefreshKey(state: PagingState<String, Render.Item>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Render.Item> =
        try {
            val maxId = params.key
            val limit = params.loadSize
            val statuses =
                GuestMastodonService.userTimeline(
                    user_id = userId,
                    limit = limit,
                    max_id = maxId,
                    only_media = onlyMedia,
                )
            LoadResult.Page(
                data =
                    statuses.map {
                        it.render(GuestMastodonService.GuestKey, event)
                    },
                prevKey = null,
                nextKey = statuses.lastOrNull()?.id,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
