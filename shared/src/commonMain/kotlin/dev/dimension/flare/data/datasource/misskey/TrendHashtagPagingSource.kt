package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: MisskeyService,
) : BasePagingSource<Int, UiHashtag>() {
    override fun getRefreshKey(state: PagingState<Int, UiHashtag>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiHashtag> {
        service
            .hashtagsTrend()

            ?.map {
                UiHashtag(
                    hashtag = it.tag,
                    description = null,
                    searchContent = "#${it.tag}",
                )
            }.let {
                return LoadResult.Page(
                    data = it ?: emptyList(),
                    prevKey = null,
                    nextKey = null,
                )
            }
    }
}
