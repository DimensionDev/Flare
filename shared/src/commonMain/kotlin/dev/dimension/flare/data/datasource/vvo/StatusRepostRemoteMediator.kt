package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class StatusRepostRemoteMediator(
    private val service: VVOService,
    private val statusKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val database: CacheDatabase,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    private var page = 1

    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        val config = service.config()
        if (config.data?.login != true) {
            return MediatorResult.Error(
                LoginExpiredException,
            )
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
                    return MediatorResult.Success(
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

        val status =
            response.data
                ?.data
                .orEmpty()

        database.connect {
            if (loadType == LoadType.REFRESH) {
                database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
            }
            VVO.saveStatus(
                database = database,
                accountKey = accountKey,
                pagingKey = pagingKey,
                statuses = status,
                sortIdProvider = {
                    val index = status.indexOf(it)
                    -(index + page * state.config.pageSize).toLong()
                },
            )
        }

        return MediatorResult.Success(
            endOfPaginationReached = status.isEmpty(),
        )
    }
}
