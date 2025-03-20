package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: XQTService,
) : BasePagingSource<Int, UiHashtag>() {
    override fun getRefreshKey(state: PagingState<Int, UiHashtag>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiHashtag> {
        service
            .getGuide(count = params.loadSize)
            .timeline
            ?.instructions
            ?.asSequence()
            ?.mapNotNull {
                it.addEntries?.entries
            }?.flatten()
            ?.mapNotNull {
                it.content?.timelineModule?.items
            }?.flatten()
            ?.mapNotNull {
                it.item?.content?.trend
            }?.map {
                UiHashtag(
                    hashtag = it.name.orEmpty(),
                    description = null,
                    searchContent = it.name.orEmpty(),
                )
            }?.toList()
            .orEmpty()
            .let {
                return LoadResult.Page(
                    data = it,
                    prevKey = null,
                    nextKey = null,
                )
            }
    }
}
