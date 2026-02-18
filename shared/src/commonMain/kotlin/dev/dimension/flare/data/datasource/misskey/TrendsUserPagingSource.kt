package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.PinnedUsersRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
) : BasePagingSource<Int, UiProfile>() {
    override fun getRefreshKey(state: PagingState<Int, UiProfile>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiProfile> {
        service
            .pinnedUsers(PinnedUsersRequest(limit = params.loadSize))
            .map {
                it.render(accountKey)
            }.let {
                return LoadResult.Page(
                    data = it,
                    prevKey = null,
                    nextKey = null,
                )
            }
    }
}
