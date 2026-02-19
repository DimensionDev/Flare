package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.PagingState
import app.bsky.graph.GetFollowersQueryParams
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render
import sh.christian.ozone.api.Did

internal class FansPagingSource(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : BasePagingSource<String, UiProfile>() {
    override fun getRefreshKey(state: PagingState<String, UiProfile>): String? = null

    override suspend fun doLoad(params: LoadParams<String>): LoadResult<String, UiProfile> {
        val cursor = params.key
        val limit = params.loadSize
        val response =
            service
                .getFollowers(
                    params =
                        GetFollowersQueryParams(
                            actor = Did(userKey.id),
                            limit = limit.toLong(),
                            cursor = cursor,
                        ),
                ).requireResponse()
        return LoadResult.Page(
            data =
                response.followers.map {
                    it.render(accountKey = accountKey)
                },
            prevKey = null,
            nextKey = response.cursor,
        )
    }
}
