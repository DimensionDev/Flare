package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersFollowersRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class FansPagingSource(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val maxId =
            when (request) {
                PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
                is PagingRequest.Append -> request.nextKey
            }
        val response =
            service
                .usersFollowers(
                    usersFollowersRequest =
                        UsersFollowersRequest(
                            untilId = maxId,
                            limit = pageSize,
                            userId = userKey.id,
                        ),
                ).orEmpty()
        return PagingResult(
            data =
                response.mapNotNull {
                    it.follower?.render(accountKey = accountKey)
                },
            nextKey = response.lastOrNull()?.id,
        )
    }
}
