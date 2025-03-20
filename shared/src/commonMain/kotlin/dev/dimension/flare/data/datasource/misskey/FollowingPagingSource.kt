package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersFollowersRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render

internal class FollowingPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : BasePagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        val maxId = params.key
        val limit = params.loadSize
        val response =
            service
                .usersFollowing(
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
                    // TODO: isn't followee a typo?
                    it.followee?.render(accountKey = accountKey)
                },
            prevKey = null,
            nextKey = response?.lastOrNull()?.id,
        )
    }
}
