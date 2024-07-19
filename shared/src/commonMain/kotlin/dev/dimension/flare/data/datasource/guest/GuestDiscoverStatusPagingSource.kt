package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.render.Render

internal class GuestDiscoverStatusPagingSource(
    private val event: StatusEvent.Mastodon,
) : PagingSource<Int, Render.Item>() {
    override fun getRefreshKey(state: PagingState<Int, Render.Item>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Render.Item> =
        try {
            val result =
                GuestMastodonService.trendsStatuses(
                    limit = params.loadSize,
                    offset = params.key,
                )

            LoadResult.Page(
                data = result.map { it.render(GuestMastodonService.GuestKey, event) },
                prevKey = null,
                nextKey = result.size + (params.key ?: 0),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
