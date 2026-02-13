package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.GetTimelineQueryParams
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
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
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val response =
            when (request) {
                is PagingRequest.Prepend -> return PagingResult(
                    endOfPaginationReached = true,
                )

                PagingRequest.Refresh -> {
                    service
                        .getTimeline(
                            GetTimelineQueryParams(
                                limit = pageSize.toLong(),
                            ),
                        ).maybeResponse()
                }

                is PagingRequest.Append -> {
                    service
                        .getTimeline(
                            GetTimelineQueryParams(
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).maybeResponse()
                }
            } ?: return PagingResult(
                endOfPaginationReached = true,
            )
        return PagingResult(
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
