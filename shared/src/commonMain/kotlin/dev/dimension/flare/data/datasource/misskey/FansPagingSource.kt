package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersFollowersRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class FansPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : BasePagingSource<String, UiProfile>() {
    override fun getRefreshKey(state: PagingState<String, UiProfile>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiProfile> {
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
                response.orEmpty().mapNotNull {
                    it.follower?.render(accountKey = accountKey)
                },
            prevKey = null,
            nextKey = response.lastOrNull()?.id,
        )
    }
}
