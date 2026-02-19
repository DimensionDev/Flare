package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.mastodon.api.SearchResources
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: SearchResources,
    private val host: String,
    private val accountKey: MicroBlogKey?,
    private val query: String,
    private val following: Boolean = false,
    private val resolve: Boolean? = null,
) : BasePagingSource<String, UiProfile>() {
    override fun getRefreshKey(state: PagingState<String, UiProfile>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiProfile> {
        service
            .searchV2(
                query = query,
                limit = params.loadSize,
                max_id = params.key,
                type = "accounts",
                following = following,
                resolve = resolve,
            ).accounts
            ?.let { accounts ->
                return LoadResult.Page(
                    data = accounts.map { it.render(accountKey = accountKey, host = host) },
                    prevKey = null,
                    nextKey = accounts.lastOrNull()?.id?.takeIf { it != params.key && accounts.size == params.loadSize },
                )
            } ?: run {
            return LoadResult.Error(Exception("No data"))
        }
    }
}
