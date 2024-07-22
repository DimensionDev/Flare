package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserPagingSource(
    private val service: TrendsResources,
    private val host: String,
) : PagingSource<Int, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<Int, UiUserV2>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiUserV2> {
        try {
            service
                .suggestionsUsers()
                .mapNotNull {
                    it.account?.render(host)
                }.let {
                    return LoadResult.Page(
                        data = it,
                        prevKey = null,
                        nextKey = null,
                    )
                }
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
