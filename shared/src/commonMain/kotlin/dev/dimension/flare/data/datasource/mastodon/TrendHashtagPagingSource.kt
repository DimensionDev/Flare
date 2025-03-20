package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: TrendsResources,
) : BasePagingSource<Int, UiHashtag>() {
    override fun getRefreshKey(state: PagingState<Int, UiHashtag>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiHashtag> {
        service
            .trendsTags()
            .map {
                UiHashtag(
                    hashtag = it.name ?: "",
                    description = null,
                    searchContent = "#${it.name}",
                )
            }.let {
                return LoadResult.Page(
                    data = it,
                    prevKey = null,
                    nextKey = null,
                )
            }
    }
}
