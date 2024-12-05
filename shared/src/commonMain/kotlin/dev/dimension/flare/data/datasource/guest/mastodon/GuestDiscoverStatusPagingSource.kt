package dev.dimension.flare.data.datasource.guest.mastodon

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.renderGuest

internal class GuestDiscoverStatusPagingSource(
    private val service: TrendsResources,
    private val host: String,
) : PagingSource<Int, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiTimeline> =
        try {
            val result =
                service.trendsStatuses(
                    limit = params.loadSize,
                    offset = params.key,
                )

            LoadResult.Page(
                data = result.map { it.renderGuest(host = host) },
                prevKey = null,
                nextKey = result.size + (params.key ?: 0),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
