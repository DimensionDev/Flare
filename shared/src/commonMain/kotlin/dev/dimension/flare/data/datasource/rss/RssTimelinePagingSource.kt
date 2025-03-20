package dev.dimension.flare.data.datasource.rss

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class RssTimelinePagingSource(
    private val url: String,
) : BasePagingSource<Int, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiTimeline> {
        val response = RssService.fetch(url).render()
        return LoadResult.Page(
            data = response,
            prevKey = null,
            nextKey = null,
        )
    }
}
