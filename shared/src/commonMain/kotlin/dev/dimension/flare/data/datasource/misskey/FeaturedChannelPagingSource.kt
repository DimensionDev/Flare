package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.ChannelsFeaturedRequest
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render

internal class FeaturedChannelPagingSource(
    private val service: MisskeyService,
) : BasePagingSource<Int, UiList.Channel>() {
    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiList.Channel> =
        tryRun {
            service
                .channelsFeatured(
                    request = ChannelsFeaturedRequest(),
                ).map {
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

    override fun getRefreshKey(state: PagingState<Int, UiList.Channel>): Int? = null
}
