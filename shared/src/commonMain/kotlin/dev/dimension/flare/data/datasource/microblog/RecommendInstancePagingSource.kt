package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiInstance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class RecommendInstancePagingSource(
    private val platformRegistry: PlatformRegistry,
) : BasePagingSource<Int, UiInstance>() {
    override fun getRefreshKey(state: PagingState<Int, UiInstance>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiInstance> {
        val recommendations =
            coroutineScope {
                platformRegistry.all
                    .map { spec ->
                        async {
                            tryRun {
                                spec.recommendInstances()
                            }.getOrDefault(emptyList())
                        }
                    }.awaitAll()
                    .flatten()
            }
        val instances =
            recommendations
                .sortedWith(
                    compareByDescending<RecommendedInstance> { it.priority }
                        .thenByDescending { it.instance.usersCount },
                ).distinctBy { it.instance.type to it.instance.domain }
                .map { it.instance }
        return LoadResult.Page(
            data = instances,
            prevKey = null,
            nextKey = null,
        )
    }
}
