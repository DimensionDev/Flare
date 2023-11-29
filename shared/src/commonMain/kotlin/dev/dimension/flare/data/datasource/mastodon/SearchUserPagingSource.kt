package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi

internal class SearchUserPagingSource(
    private val service: MastodonService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : PagingSource<String, UiUser>() {
    override fun getRefreshKey(state: PagingState<String, UiUser>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUser> {
        try {
            service.searchV2(
                query = query,
                limit = params.loadSize,
                max_id = params.key,
                type = "accounts",
            ).accounts?.let {
                return LoadResult.Page(
                    data = it.map { it.toUi(accountKey.host) },
                    prevKey = null,
                    nextKey = it.lastOrNull()?.id,
                )
            } ?: run {
                return LoadResult.Error(Exception("No data"))
            }
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
