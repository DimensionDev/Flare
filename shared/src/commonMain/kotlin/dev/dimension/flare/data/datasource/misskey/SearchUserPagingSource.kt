package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersSearchRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi

internal class SearchUserPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : PagingSource<Int, UiUser>() {
    override fun getRefreshKey(state: PagingState<Int, UiUser>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiUser> {
        try {
            service.usersSearch(
                UsersSearchRequest(
                    query = query,
                    limit = params.loadSize,
                    offset = params.key,
                ),
            ).body()?.let {
                return LoadResult.Page(
                    data = it.map { it.toUi(accountKey.host) },
                    prevKey = null,
                    nextKey = (params.key ?: 0) + params.loadSize,
                )
            } ?: run {
                return LoadResult.Error(Exception("No data"))
            }
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
