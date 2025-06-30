package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val inAppNotification: InAppNotification,
) : BaseTimelineRemoteMediator(
        database = database,
        clearWhenRefresh = true,
        pagingKey = pagingKey,
        accountType = AccountType.Specific(accountKey),
    ) {
    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val config = service.config()
        if (config.data?.login != true) {
            inAppNotification.onError(
                Message.LoginExpired,
                LoginExpiredException,
            )
            throw LoginExpiredException
        }

        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    service.getFriendsTimeline()
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    service.getFriendsTimeline(
                        maxId = lastItem.timeline.statusKey.id,
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

        return Result(
            endOfPaginationReached = response.data?.nextCursorStr == null,
            data = data,
        )
    }
}
