package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersSearchRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class SearchUserPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BasePagingSource<Int, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<Int, UiUserV2>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiUserV2> {
        service
            .usersSearch(
                UsersSearchRequest(
                    query = query,
                    limit = params.loadSize,
                    offset = params.key ?: 0,
                ),
            )?.let {
                return LoadResult.Page(
                    data = it.map { it.render(accountKey) },
                    prevKey = null,
                    nextKey =
                        if (it.isEmpty()) {
                            null
                        } else {
                            (params.key ?: 0) + params.loadSize
                        },
                )
            } ?: run {
            return LoadResult.Error(Exception("No data"))
        }
    }
}
