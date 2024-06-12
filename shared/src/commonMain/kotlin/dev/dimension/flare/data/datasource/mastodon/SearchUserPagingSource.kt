package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.api.SearchResources
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi

internal class SearchUserPagingSource(
    private val service: SearchResources,
    private val host: String,
    private val query: String,
) : PagingSource<String, UiUser>() {
    override fun getRefreshKey(state: PagingState<String, UiUser>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUser> {
        try {
            service
                .searchV2(
                    query = query,
                    limit = params.loadSize,
                    max_id = params.key,
                    type = "accounts",
                ).accounts
                ?.let {
                    return LoadResult.Page(
                        data = it.map { it.toUi(host) },
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
