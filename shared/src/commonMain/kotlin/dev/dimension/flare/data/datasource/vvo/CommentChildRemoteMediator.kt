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
import dev.dimension.flare.model.PlatformType

@OptIn(ExperimentalPagingApi::class)
internal class CommentChildRemoteMediator(
    private val service: VVOService,
    private val commentKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String = "status_comments_child_${commentKey}_$accountKey"
    private var maxId: Long? = null
    private var page = 0

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    page = 0
                    service
                        .getHotFlowChild(
                            cid = commentKey.id,
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    page++
                    service.getHotFlowChild(
                        cid = commentKey.id,
                        maxId = maxId,
                    )
                }
            }

        maxId = response.maxID?.takeIf { it != 0L }
        val status = response.data.orEmpty()

        val data =
            status.map { comment ->
                comment.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = {
                        val index = status.indexOf(it)
                        -(index + page * state.config.pageSize).toLong()
                    },
                )
            }

        return Result(
            endOfPaginationReached = maxId == null,
            data = data,
        )
    }
}
