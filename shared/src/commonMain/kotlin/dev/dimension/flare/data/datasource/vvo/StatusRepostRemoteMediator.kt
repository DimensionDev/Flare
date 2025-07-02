package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class StatusRepostRemoteMediator(
    private val service: VVOService,
    private val statusKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "status_reposts_${statusKey}_$accountKey"
    private var page = 1

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException
        }

        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    page = 1
                    service
                        .getRepostTimeline(
                            id = statusKey.id,
                            page = page,
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    page++
                    service.getRepostTimeline(
                        id = statusKey.id,
                        page = page,
                    )
                }
            }

        val statuses =
            response.data
                ?.data
                .orEmpty()

        val data =
            statuses.map { status ->
                status.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = { item ->
                        val index = statuses.indexOf(item)
                        -(index + page * state.config.pageSize).toLong()
                    },
                )
            }

        return Result(
            endOfPaginationReached = statuses.isEmpty(),
            data = data,
        )
    }
}
