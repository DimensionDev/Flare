package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import app.bsky.graph.GetFollowsQueryParams
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render
import sh.christian.ozone.api.Did

internal class FollowingPagingSource(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : PagingSource<String, UiUserV2>() {
    override fun getRefreshKey(state: PagingState<String, UiUserV2>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, UiUserV2> {
        try {
            val cursor = params.key
            val limit = params.loadSize
            val response =
                service
                    .getFollows(
                        params =
                            GetFollowsQueryParams(
                                actor = Did(userKey.id),
                                limit = limit.toLong(),
                                cursor = cursor,
                            ),
                    ).requireResponse()
            return LoadResult.Page(
                data =
                    response.follows.map {
                        it.render(accountKey = accountKey)
                    },
                prevKey = null,
                nextKey = response.cursor,
            )
        } catch (e: Throwable) {
            return LoadResult.Error(e)
        }
    }
}
