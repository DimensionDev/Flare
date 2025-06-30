package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import app.bsky.feed.GetTimelineQueryParams
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val inAppNotification: InAppNotification,
) : BaseTimelineRemoteMediator(
        database = database,
        clearWhenRefresh = true,
        pagingKey = pagingKey,
        accountType = AccountType.Specific(accountKey),
    ) {
    var cursor: String? = null

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.PREPEND -> return Result(
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
            } ?: return Result(
                endOfPaginationReached = true,
            )
        cursor = response.cursor
        return Result(
            endOfPaginationReached = cursor == null,
            data =
                Bluesky.saveFeed(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    data = response.feed,
                ),
        )
    }

    override fun onError(e: Throwable) {
        if (e is LoginExpiredException) {
            inAppNotification.onError(
                Message.LoginExpired,
                LoginExpiredException,
            )
        }
    }
}
