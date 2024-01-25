package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: XQTService,
) : PagingSource<Int, UiHashtag>() {
    override fun getRefreshKey(state: PagingState<Int, UiHashtag>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiHashtag> {
        try {
            service.getGuide(count = params.loadSize)
                .timeline
                ?.instructions
                ?.asSequence()
                ?.mapNotNull {
                    it.addEntries?.entries
                }
                ?.flatten()
                ?.mapNotNull {
                    it.content?.timelineModule?.items
                }
                ?.flatten()
                ?.mapNotNull {
                    it.item?.content?.trend
                }
                ?.map {
                    UiHashtag(
                        hashtag = it.name ?: "",
                        description = null,
                    )
                }
                ?.toList()
                .orEmpty()
                .let {
                    return LoadResult.Page(
                        data = it,
                        prevKey = null,
                        nextKey = null,
                    )
                }
        } catch (e: Throwable) {
            e.printStackTrace()
            return LoadResult.Error(e)
        }
    }
}
