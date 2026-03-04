package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class UserRepliesTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "user_replies_${userKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getUserTweetsAndReplies(
                        variables =
                            UserTimelineRequest(
                                userID = userKey.id,
                                count = pageSize.toLong(),
                            ).encodeJson(),
                    )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.getUserTweetsAndReplies(
                        variables =
                            UserTimelineRequest(
                                userID = userKey.id,
                                count = pageSize.toLong(),
                                cursor = request.nextKey,
                            ).encodeJson(),
                    )
                }
            }.body()
        val instructions =
            response
                ?.data
                ?.user
                ?.result
                ?.timelineV2
                ?.timeline
                ?.instructions
                .orEmpty()
        val tweet =
            instructions.tweets(
                includePin = request is PagingRequest.Refresh,
            )

        return PagingResult(
            endOfPaginationReached = tweet.isEmpty(),
            data = tweet.mapNotNull { it.render(accountKey) },
            nextKey = instructions.cursor(),
        )
    }
}
