package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class DeviceFollowRemoteMediator(
    private val service: XQTService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "device_follow_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .getNotificationsDeviceFollow(
                            count = pageSize,
                        )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.getNotificationsDeviceFollow(
                        count = pageSize,
                        cursor = request.nextKey,
                    )
                }
            }
        val tweets = response.tweets()

        val data = tweets.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return PagingResult(
            endOfPaginationReached = tweets.isEmpty(),
            data = data,
            nextKey = response.cursor(),
        )
    }
}
