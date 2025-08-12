package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class MentionRemoteMediator(
    private val service: XQTService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "mention_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getNotificationsMentions(
                            count = pageSize,
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.getNotificationsMentions(
                        count = pageSize,
                        cursor = request.nextKey,
                    )
                }
            }
        val tweets = response.tweets()

        val data = tweets.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = tweets.isEmpty(),
            data = data,
            nextKey = response.cursor(),
        )
    }
}
