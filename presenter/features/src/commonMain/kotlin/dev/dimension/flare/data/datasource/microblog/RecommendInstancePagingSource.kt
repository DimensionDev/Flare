package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.ui.model.UiInstance

internal class RecommendInstancePagingSource(
    private val platformRegistry: SocialPlatformRegistry,
) : BasePagingSource<Int, UiInstance>() {
    override fun getRefreshKey(state: PagingState<Int, UiInstance>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiInstance> =
        LoadResult.Page(
            data = platformRegistry.recommendedInstances(),
            prevKey = null,
            nextKey = null,
        )
}
