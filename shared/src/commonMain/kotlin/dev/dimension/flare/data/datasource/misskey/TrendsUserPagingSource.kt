package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.PinnedUsersRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi

internal class TrendsUserPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
) : PagingSource<Int, UiUser>() {
    override fun getRefreshKey(state: PagingState<Int, UiUser>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiUser> {
        try {
            service.pinnedUsers(PinnedUsersRequest(limit = params.loadSize)).body()?.map {
                it.toUi(accountKey.host)
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
