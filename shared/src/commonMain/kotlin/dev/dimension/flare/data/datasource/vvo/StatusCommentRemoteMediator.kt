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
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class StatusCommentRemoteMediator(
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val service: VVOService,
    private val statusKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
        clearWhenRefresh = true,
        pagingKey = pagingKey,
        accountType = AccountType.Specific(accountKey),
    ) {
    private var maxId: Long? = null
    private var page = 0

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
                    service
                        .getHotComments(
                            id = statusKey.id,
                            mid = statusKey.id,
                            maxId = null,
                        )
                }
                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    page++
                    service.getHotComments(
                        id = statusKey.id,
                        mid = statusKey.id,
                        maxId = maxId,
                    )
                }
            }

        maxId = response.data?.maxID?.takeIf { it != 0L }
        val comments = response.data?.data.orEmpty()

        val data =
            comments.map { comment ->
                comment.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = { item ->
                        val index = comments.indexOf(item)
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
