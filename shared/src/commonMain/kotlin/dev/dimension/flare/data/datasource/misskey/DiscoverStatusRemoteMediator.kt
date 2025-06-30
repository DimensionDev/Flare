package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesFeaturedRequest
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class DiscoverStatusRemoteMediator(
    private val service: MisskeyService,
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
                    service.notesFeatured(NotesFeaturedRequest(limit = state.config.initialLoadSize))
                }
                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    service.notesFeatured(
                        NotesFeaturedRequest(
                            limit = state.config.pageSize,
                            untilId =
                                lastItem
                                    .timeline
                                    .statusKey
                                    .id,
                        ),
                    )
                }
                else -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }
            } ?: emptyList()

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
