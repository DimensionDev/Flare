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
internal class DiscoverStatusRemoteMediator(
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "discover_status_$accountKey"
    private var page = 0
    private val containerId = "102803"

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
                    page = 0
                    service.getContainerIndex(containerId = containerId)
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    page++
                    service.getContainerIndex(containerId = containerId, sinceId = page.toString())
                }
            }

        val status =
            response.data
                ?.cards
                ?.mapNotNull { it.mblog }
                .orEmpty()

        val data =
            status.map { statusItem ->
                statusItem.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = {
                        val index = status.indexOf(it)
                        -(index + page * state.config.pageSize).toLong()
                    },
                )
            }

        return Result(
            endOfPaginationReached = status.isEmpty(),
            data = data,
        )
    }
}
