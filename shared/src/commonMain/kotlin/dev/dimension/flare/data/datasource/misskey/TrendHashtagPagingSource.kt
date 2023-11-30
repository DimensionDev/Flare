package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: MisskeyService,
) : PagingSource<Int, UiHashtag>() {
    override fun getRefreshKey(state: PagingState<Int, UiHashtag>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiHashtag> {
        try {
            service.hashtagsTrend().body()?.map {
                UiHashtag(
                    hashtag = it.tag,
                    description = null,
                )
            }.let {
                return LoadResult.Page(
                    data = it ?: emptyList(),
                    prevKey = null,
                    nextKey = null,
                )
            }
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
