package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.users
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render

internal class FollowingPagingSource(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : RemoteLoader<UiProfile> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val cursor =
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
                .getFollowing(
                    variables =
                        FollowVar(
                            userID = userKey.id,
                            count = pageSize.toLong(),
                            cursor = cursor,
                        ).encodeJson(),
                ).body()
        val users =
            response
                ?.data
                ?.user
                ?.result
                ?.timeline
                ?.timeline
                ?.instructions
                ?.users()
                .orEmpty()
        val nextCursor =
            response
                ?.data
                ?.user
                ?.result
                ?.timeline
                ?.timeline
                ?.instructions
                ?.cursor()
        return PagingResult(
            data = users.map { it.render(accountKey = accountKey) },
            nextKey = nextCursor,
        )
    }
}
