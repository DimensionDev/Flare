package dev.dimension.flare.data.datasource.rss

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class RssTimelinePagingSource(
    private val url: String,
) : PagingSource<Int, UiTimeline>() {
    override fun getRefreshKey(state: PagingState<Int, UiTimeline>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiTimeline> {
        return try {
            val response = RssService.fetch(url).render()
            return LoadResult.Page(
                data = response,
                prevKey = null,
                nextKey = null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
