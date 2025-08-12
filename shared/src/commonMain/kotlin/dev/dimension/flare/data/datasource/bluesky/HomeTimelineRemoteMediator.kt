package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.GetTimelineQueryParams
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val inAppNotification: InAppNotification,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "home_$accountKey"

    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                is Request.Prepend -> return Result(
                    endOfPaginationReached = true,
                )

                Request.Refresh -> {
                    service
                        .getTimeline(
                            GetTimelineQueryParams(
                                limit = pageSize.toLong(),
                            ),
                        ).maybeResponse()
                }

                is Request.Append -> {
                    service
                        .getTimeline(
                            GetTimelineQueryParams(
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).maybeResponse()
                }
            } ?: return Result(
                endOfPaginationReached = true,
            )
        return Result(
            endOfPaginationReached = response.cursor == null,
            data =
                response.feed.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.cursor,
        )
    }

    override fun onError(e: Throwable) {
        if (e is LoginExpiredException) {
            inAppNotification.onError(
                Message.LoginExpired,
                LoginExpiredException(
                    accountKey = accountKey,
                    platformType = PlatformType.Bluesky,
                ),
            )
        }
    }
}
