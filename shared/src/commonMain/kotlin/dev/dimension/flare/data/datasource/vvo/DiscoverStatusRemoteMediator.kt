package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class DiscoverStatusRemoteMediator(
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    private var page = 0
    private val containerId = "102803"

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
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
                        page = 0
                        service.getContainerIndex(containerId = containerId).also {
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
                        service.getContainerIndex(containerId = containerId, sinceId = page.toString())
                    }
                }

            val status =
                response.data
                    ?.cards
                    ?.mapNotNull { it.mblog }
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
