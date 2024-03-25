package dev.dimension.flare.data.datasource.guest

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.mapper.toUi

internal class GuestTimelinePagingSource : PagingSource<Int, UiStatus>() {
    override fun getRefreshKey(state: PagingState<Int, UiStatus>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiStatus> {
        return try {
            val offset = params.key ?: 0
            val limit = params.loadSize
            val statuses = GuestMastodonService.trendsStatuses(limit = limit, offset = offset)
            LoadResult.Page(
                data =
                    statuses.map {
                        it.toUi(GuestMastodonService.GuestKey)
                    },
                prevKey = null,
                nextKey = offset + limit,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
