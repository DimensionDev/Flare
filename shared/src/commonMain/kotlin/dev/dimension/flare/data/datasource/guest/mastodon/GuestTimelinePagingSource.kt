package dev.dimension.flare.data.datasource.guest.mastodon

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.renderGuest

internal class GuestTimelinePagingSource(
    private val service: TrendsResources,
    private val host: String,
) : BasePagingSource<Int, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiTimeline> {
        val offset = params.key ?: 0
        val limit = params.loadSize
        val statuses = service.trendsStatuses(limit = limit, offset = offset).distinctBy { it.id }
        return LoadResult.Page(
            data =
                statuses.map { it.renderGuest(host = host) },
            prevKey = null,
            nextKey = offset + limit,
        )
    }
}
