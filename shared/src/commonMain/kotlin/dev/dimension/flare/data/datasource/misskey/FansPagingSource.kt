package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersFollowersRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class FansPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : PagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        try {
            val maxId = params.key
            val limit = params.loadSize
            val response =
                service
                    .usersFollowers(
                        usersFollowersRequest =
                            UsersFollowersRequest(
                                untilId = maxId,
                                limit = limit,
                                userId = userKey.id,
                            ),
                    )
            return LoadResult.Page(
                data =
                    response.body().orEmpty().mapNotNull {
                        it.follower?.render(accountKey = accountKey)
                    },
                prevKey = null,
                nextKey = response.body()?.lastOrNull()?.id,
            )
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
