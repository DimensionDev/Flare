package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.bsky.feed.GetTimelineQueryParams
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val inAppNotification: InAppNotification,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.PREPEND -> return MediatorResult.Success(
                        endOfPaginationReached = true,
                    )

                    LoadType.REFRESH -> {
                        service
                            .getTimeline(
                                GetTimelineQueryParams(
                                    algorithm = "reverse-chronological",
                                    limit = state.config.pageSize.toLong(),
                                ),
                            ).maybeResponse()
                    }

                    LoadType.APPEND -> {
                        service
                            .getTimeline(
                                GetTimelineQueryParams(
                                    algorithm = "reverse-chronological",
                                    limit = state.config.pageSize.toLong(),
                                    cursor = cursor,
                                ),
                            ).maybeResponse()
                    }
                } ?: return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            if (loadType == LoadType.REFRESH) {
                database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
            }
            cursor = response.cursor
            Bluesky.saveFeed(
                accountKey,
                pagingKey,
                database,
                response.feed,
            )

            MediatorResult.Success(
                endOfPaginationReached = cursor == null,
            )
        } catch (e: Throwable) {
            if (e is LoginExpiredException) {
                inAppNotification.onError(
                    Message.LoginExpired,
                    LoginExpiredException,
                )
            }
            MediatorResult.Error(e)
        }
    }
}
