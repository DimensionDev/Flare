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
internal class CommentChildRemoteMediator(
    private val service: VVOService,
    private val commentKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    database: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "status_comments_child_${commentKey}_$accountKey"

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
                        .getHotFlowChild(
                            cid = commentKey.id,
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.getHotFlowChild(
                        cid = commentKey.id,
                        maxId = request.nextKey.toLongOrNull(),
                    )
                }
            }

        val maxId = response.maxID?.takeIf { it != 0L }
        val status = response.data.orEmpty()

        val data =
            status.map { comment ->
                comment.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = {
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
