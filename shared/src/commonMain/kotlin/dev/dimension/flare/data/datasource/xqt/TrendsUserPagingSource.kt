package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi

internal class TrendsUserPagingSource(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : PagingSource<Int, UiUser>() {
    override fun getRefreshKey(state: PagingState<Int, UiUser>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiUser> {
        try {
            service.getUserRecommendations(
                limit = params.loadSize,
                userId = accountKey.id,
            ).mapNotNull {
                if (it.user != null && it.userID != null) {
                    User(
                        legacy = it.user,
                        restId = it.userID,
                    ).toUi(accountKey = accountKey)
                } else {
                    null
                }
            }.let {
                return LoadResult.Page(
                    data = it,
                    prevKey = null,
                    nextKey = null,
                )
            }
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
