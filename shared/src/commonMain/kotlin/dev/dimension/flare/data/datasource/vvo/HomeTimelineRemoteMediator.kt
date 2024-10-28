package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val inAppNotification: InAppNotification,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
            val config = service.config()
            if (config.data?.login != true) {
                inAppNotification.onError(
                    Message.LoginExpired,
                    LoginExpiredException,
                )
                return MediatorResult.Error(
                    LoginExpiredException,
                )
            }
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        service.getFriendsTimeline().also {
                            database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
                        }
                    }

                    LoadType.PREPEND -> {
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    }

                    LoadType.APPEND -> {
                        val lastItem =
                            state.lastItemOrNull()
                                ?: return MediatorResult.Success(
                                    endOfPaginationReached = true,
                                )
                        service.getFriendsTimeline(
                            maxId = lastItem.timeline.statusKey.id,
                        )
                    }
                }

            VVO.saveStatus(
                accountKey = accountKey,
                pagingKey = pagingKey,
                database = database,
                statuses = response.data?.statuses.orEmpty(),
            )

            MediatorResult.Success(
                endOfPaginationReached = response.data?.nextCursorStr == null,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
