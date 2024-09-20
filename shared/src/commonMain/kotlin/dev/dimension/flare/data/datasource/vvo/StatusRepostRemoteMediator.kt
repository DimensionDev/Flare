package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
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
) : RemoteMediator<Int, DbPagingTimelineView>() {
    private var page = 1

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineView>,
    ): MediatorResult {
        return try {
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
                            ).also {
                                database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
                            }
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

            MediatorResult.Success(
                endOfPaginationReached = status.isEmpty(),
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
