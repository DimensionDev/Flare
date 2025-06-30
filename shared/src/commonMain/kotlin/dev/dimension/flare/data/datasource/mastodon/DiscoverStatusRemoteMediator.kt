package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class DiscoverStatusRemoteMediator(
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
) : BaseTimelineRemoteMediator(
        database = database,
        clearWhenRefresh = true,
        pagingKey = pagingKey,
        accountType = AccountType.Specific(accountKey),
    ) {
    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    service.trendsStatuses()
                }
                else -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }
            }

        return Result(
            endOfPaginationReached = true,
            data =
                response.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
        )
    }
}
