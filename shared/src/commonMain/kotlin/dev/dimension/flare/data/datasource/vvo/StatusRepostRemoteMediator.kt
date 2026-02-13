package dev.dimension.flare.data.datasource.vvo

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
                PagingRequest.Refresh -> 1
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
                is PagingRequest.Prepend -> return PagingResult(endOfPaginationReached = true)
            }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .getRepostTimeline(
                            id = statusKey.id,
                            page = page,
                        )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
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

        return PagingResult(
            endOfPaginationReached = statuses.isEmpty(),
            data = data,
            nextKey = (page + 1).toString(),
        )
    }
}
