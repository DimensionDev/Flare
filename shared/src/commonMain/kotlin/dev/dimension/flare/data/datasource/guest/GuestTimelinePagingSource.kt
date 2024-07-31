package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render

internal class GuestTimelinePagingSource(
    private val event: StatusEvent.Mastodon,
) : PagingSource<Int, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiTimeline> =
        try {
            val offset = params.key ?: 0
            val limit = params.loadSize
            val statuses = GuestMastodonService.trendsStatuses(limit = limit, offset = offset)
            LoadResult.Page(
                data =
                    statuses.map {
                        it.render(GuestMastodonService.GuestKey, event)
                    },
                prevKey = null,
                nextKey = offset + limit,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
