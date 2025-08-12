package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

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

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                Request.Refresh -> 1
                is Request.Append -> request.nextKey.toIntOrNull() ?: 1
                is Request.Prepend -> return Result(endOfPaginationReached = true)
            }

        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getRepostTimeline(
                            id = statusKey.id,
                            page = page,
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
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
                        -(index + page * pageSize).toLong()
                    },
                )
            }

        return Result(
            endOfPaginationReached = statuses.isEmpty(),
            data = data,
            nextKey = (page + 1).toString(),
        )
    }
}
