package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.api.SearchResources
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: SearchResources,
    private val accountKey: MicroBlogKey,
    private val query: String,
    private val following: Boolean = false,
    private val resolve: Boolean? = null,
) : PagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        try {
            service
                .searchV2(
                    query = query,
                    limit = params.loadSize,
                    max_id = params.key,
                    type = "accounts",
                    following = following,
                    resolve = resolve,
                ).accounts
                ?.let {
                    return LoadResult.Page(
                        data = it.map { it.render(accountKey) },
                        prevKey = null,
                        nextKey = it.lastOrNull()?.id?.takeIf { it != params.key },
                    )
                } ?: run {
                return LoadResult.Error(Exception("No data"))
            }
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
