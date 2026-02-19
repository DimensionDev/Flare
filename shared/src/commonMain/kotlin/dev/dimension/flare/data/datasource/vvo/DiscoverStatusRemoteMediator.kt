package dev.dimension.flare.data.datasource.vvo

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

@OptIn(ExperimentalPagingApi::class)
internal class DiscoverStatusRemoteMediator(
    private val service: VVOService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "discover_status_$accountKey"
    private val containerId = "102803"

    override suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 0
                is PagingRequest.Prepend -> 0
                PagingRequest.Refresh -> 0
            }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getContainerIndex(containerId = containerId)
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
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
                        -SnowflakeIdGenerator.nextId()
                    },
                )
            }

        return PagingResult(
            endOfPaginationReached = status.isEmpty(),
            data = data,
            nextKey = (page + 1).toString(),
        )
    }
}
