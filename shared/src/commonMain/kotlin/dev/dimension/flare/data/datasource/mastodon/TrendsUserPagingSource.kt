package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserPagingSource(
    private val service: TrendsResources,
    private val accountKey: MicroBlogKey?,
    private val host: String,
) : BasePagingSource<Int, UiProfile>() {
    override fun getRefreshKey(state: PagingState<Int, UiProfile>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiProfile> {
        service
            .suggestionsUsers()
            .mapNotNull {
                it.account?.render(accountKey = accountKey, host = host)
            }.let {
                return LoadResult.Page(
                    data = it,
                    prevKey = null,
                    nextKey = null,
                )
            }
    }
}
