package dev.dimension.flare.data.datasource.vvo

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

@OptIn(ExperimentalPagingApi::class)
internal class StatusCommentRemoteMediator(
    database: CacheDatabase,
    private val service: VVOService,
    private val statusKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "status_comments_${statusKey}_$accountKey"

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

        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getHotComments(
                            id = statusKey.id,
                            mid = statusKey.id,
                            maxId = null,
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.getHotComments(
                        id = statusKey.id,
                        mid = statusKey.id,
                        maxId = request.nextKey.toLongOrNull(),
                    )
                }
            }

        val maxId = response.data?.maxID?.takeIf { it != 0L }
        val comments = response.data?.data.orEmpty()

        val data =
            comments.map { comment ->
                comment.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = { item ->
                        -SnowflakeIdGenerator.nextId()
                    },
                )
            }

        return Result(
            endOfPaginationReached = maxId == null,
            data = data,
            nextKey = maxId?.toString(),
        )
    }
}
