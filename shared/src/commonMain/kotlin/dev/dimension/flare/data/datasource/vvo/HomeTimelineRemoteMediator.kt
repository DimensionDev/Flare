package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: VVOService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val inAppNotification: InAppNotification,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "home_$accountKey"

    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val config = service.config()
        if (config.data?.login != true) {
            inAppNotification.onError(
                Message.LoginExpired,
                LoginExpiredException(
                    accountKey = accountKey,
                    platformType = PlatformType.VVo,
                ),
            )
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getFriendsTimeline()
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.getFriendsTimeline(
                        maxId = request.nextKey,
                    )
                }
            }

        val statuses = response.data?.statuses.orEmpty()
        val data =
            statuses.map { status ->
                status.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                )
            }

        return PagingResult(
            endOfPaginationReached = response.data?.nextCursorStr == null,
            data = data,
            nextKey = response.data?.nextCursorStr,
        )
    }
}
