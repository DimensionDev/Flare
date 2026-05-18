package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.common.DebugRepository
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.ui.model.UiInstance

public class RecommendInstancePagingSource(
    private val platformRegistry: SocialPlatformRegistry,
) : PagingSource<Int, UiInstance>() {
    override fun getRefreshKey(state: PagingState<Int, UiInstance>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiInstance> =
        try {
            LoadResult.Page(
                data = platformRegistry.recommendedInstances(),
                prevKey = null,
                nextKey = null,
            )
        } catch (e: Exception) {
            DebugRepository.error(e)
            LoadResult.Error(e)
        }
}
