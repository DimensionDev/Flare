package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render

internal class AntennasListPagingSource(
    private val service: MisskeyService,
) : BasePagingSource<Int, UiList>() {
    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiList> =
        tryRun {
            service.antennasList().map {
                it.render()
            }
        }.fold(
            onSuccess = { antennas ->
                LoadResult.Page(
                    data = antennas,
                    prevKey = null,
                    nextKey = null,
                )
            },
            onFailure = { error ->
                LoadResult.Error(error)
            },
        )

    override fun getRefreshKey(state: PagingState<Int, UiList>): Int? = null
}
