package dev.dimension.flare.data.datasource.bluesky

import app.bsky.graph.GetFollowersQueryParams
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render
import sh.christian.ozone.api.Did

internal class FansPagingSource(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val service = getService()
        val response =
            when (request) {
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                PagingRequest.Refresh ->
                    service
                        .getFollowers(
                            params =
                                GetFollowersQueryParams(
                                    actor = Did(userKey.id),
                                    limit = pageSize.toLong(),
                                ),
                        ).requireResponse()

                is PagingRequest.Append ->
                    service
                        .getFollowers(
                            params =
                                GetFollowersQueryParams(
                                    actor = Did(userKey.id),
                                    limit = pageSize.toLong(),
                                    cursor = request.nextKey,
                                ),
                        ).requireResponse()
            }

        return PagingResult(
            endOfPaginationReached = response.cursor == null,
            data = response.followers.map { it.render(accountKey = accountKey) },
            nextKey = response.cursor,
        )
    }
}
