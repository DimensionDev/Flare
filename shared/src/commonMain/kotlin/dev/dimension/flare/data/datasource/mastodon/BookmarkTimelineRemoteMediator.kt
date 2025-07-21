package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class BookmarkTimelineRemoteMediator(
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "bookmark_$accountKey"

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    service
                        .bookmarks(
                            limit = state.config.pageSize,
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getPagingKey(pagingKey)?.nextKey
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    service.bookmarks(
                        limit = state.config.pageSize,
                        max_id = lastItem,
                    )
                }
            }

        return Result(
            endOfPaginationReached = response.isEmpty() || response.next == null,
            data =
                response.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.next,
        )
    }
}
