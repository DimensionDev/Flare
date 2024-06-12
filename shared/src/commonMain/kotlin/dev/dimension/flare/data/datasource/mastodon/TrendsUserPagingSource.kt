package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi

internal class TrendsUserPagingSource(
    private val service: TrendsResources,
    private val host: String,
) : PagingSource<Int, UiUser>() {
    override fun getRefreshKey(state: PagingState<Int, UiUser>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiUser> {
        try {
            service
                .suggestionsUsers()
                .mapNotNull {
                    it.account?.toUi(host)
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
