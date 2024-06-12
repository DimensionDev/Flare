package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi

internal class GuestDiscoverStatusPagingSource : PagingSource<Int, UiStatus>() {
    override fun getRefreshKey(state: PagingState<Int, UiStatus>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiStatus> =
        try {
            val result =
                GuestMastodonService.trendsStatuses(
                    limit = params.loadSize,
                    offset = params.key,
                )

            LoadResult.Page(
                data = result.map { it.toUi(GuestMastodonService.GuestKey) },
                prevKey = null,
                nextKey = result.size + (params.key ?: 0),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
