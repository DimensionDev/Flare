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
internal class SearchStatusPagingSource(
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val query: String,
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
                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.REFRESH -> {
                    if (query.startsWith("#")) {
                        service.hashtagTimeline(
                            hashtag = query.removePrefix("#"),
                            limit = state.config.pageSize,
                        )
                    } else {
                        service
                            .searchV2(
                                query = query,
                                limit = state.config.pageSize,
                                type = "statuses",
                            ).statuses
                    }
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    if (query.startsWith("#")) {
                        service.hashtagTimeline(
                            hashtag = query.removePrefix("#"),
                            limit = state.config.pageSize,
                            max_id = lastItem.timeline.statusKey.id,
                        )
                    } else {
                        service
                            .searchV2(
                                query = query,
                                limit = state.config.pageSize,
                                max_id = lastItem.timeline.statusKey.id,
                                type = "statuses",
                            ).statuses
                    }
                }
            } ?: emptyList()

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
        )
    }
}
