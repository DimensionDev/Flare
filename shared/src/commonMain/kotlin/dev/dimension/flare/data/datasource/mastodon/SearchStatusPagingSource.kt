package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Mastodon
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusPagingSource(
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val query: String,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        val response =
            when (loadType) {
                LoadType.PREPEND -> {
                    return MediatorResult.Success(
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
                    }.also {
                        database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
                    }
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return MediatorResult.Success(
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

        Mastodon.save(
            database = database,
            accountKey = accountKey,
            pagingKey = pagingKey,
            data = response,
        )

        return MediatorResult.Success(
            endOfPaginationReached = response.isEmpty(),
        )
    }
}
