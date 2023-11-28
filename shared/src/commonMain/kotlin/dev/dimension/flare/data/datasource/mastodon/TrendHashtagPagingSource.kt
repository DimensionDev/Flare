package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: MastodonService,
) : PagingSource<Int, UiHashtag>() {
    override fun getRefreshKey(state: PagingState<Int, UiHashtag>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiHashtag> {
        try {
            service.trendsTags().map {
                UiHashtag(
                    hashtag = it.name ?: "",
                    description = null,
                )
            }.let {
                return LoadResult.Page(
                    data = it,
                    prevKey = null,
                    nextKey = null,
                )
            }
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
