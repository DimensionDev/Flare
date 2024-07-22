package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.PinnedUsersRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class TrendsUserPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
) : PagingSource<Int, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<Int, UiUserV2>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiUserV2> {
        try {
            service
                .pinnedUsers(PinnedUsersRequest(limit = params.loadSize))
                .body()
                ?.map {
                    it.render(accountKey)
                }.let {
                    return LoadResult.Page(
                        data = it ?: emptyList(),
                        prevKey = null,
                        nextKey = null,
                    )
                }
        } catch (e: Throwable) {
            e.printStackTrace()
            return LoadResult.Error(e)
        }
    }
}
