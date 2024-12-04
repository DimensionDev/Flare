package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.renderGuest

internal class GuestTimelinePagingSource(
    private val service: TrendsResources,
    private val host: String,
) : PagingSource<Int, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiTimeline> =
        try {
            val offset = params.key ?: 0
            val limit = params.loadSize
            val statuses = service.trendsStatuses(limit = limit, offset = offset)
            LoadResult.Page(
                data =
                    statuses.map { it.renderGuest(host = host) },
                prevKey = null,
                nextKey = offset + limit,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
