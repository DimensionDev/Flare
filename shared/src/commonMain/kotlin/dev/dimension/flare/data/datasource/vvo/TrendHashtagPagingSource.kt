package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.ui.model.UiHashtag

internal class TrendHashtagPagingSource(
    private val service: VVOService,
) : PagingSource<Int, UiHashtag>() {
    private val containerId = "106003type=25&filter_type=realtimehot"

    override fun getRefreshKey(state: PagingState<Int, UiHashtag>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiHashtag> {
        try {
            service.getContainerIndex(containerId = containerId)
                .data
                ?.cards
                ?.flatMap {
                    it.cardGroup.orEmpty()
                }
                ?.mapNotNull {
                    it.desc
                }
                ?.map {
                    UiHashtag(
                        hashtag = it,
                        description = null,
                        searchContent = "#$it#",
                    )
                }
                ?.toList()
                ?.take(10)
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
