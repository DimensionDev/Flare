package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render

internal class GuestDiscoverStatusPagingSource(
    private val host: String,
) : PagingSource<Int, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiTimeline> =
        try {
            val result =
                GuestMastodonService.trendsStatuses(
                    limit = params.loadSize,
                    offset = params.key,
                )

            LoadResult.Page(
                data = result.map { it.render(host = host, accountKey = null, event = null) },
                prevKey = null,
                nextKey = result.size + (params.key ?: 0),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
