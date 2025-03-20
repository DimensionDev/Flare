package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val query: String,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    private var page = 1
    private val containerId by lazy {
        "100103type=1&q=$query&t="
    }

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
                        .getContainerIndex(
                            containerId = containerId,
                            pageType = "searchall",
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
                    service.getContainerIndex(
                        containerId = containerId,
                        pageType = "searchall",
                        page = page,
                    )
                }
            }
        val status =
            response.data
                ?.cards
                ?.flatMap { listOfNotNull(it.mblog) + it.cardGroup?.mapNotNull { it.mblog }.orEmpty() }
                .orEmpty()

        VVO.saveStatus(
            accountKey = accountKey,
            pagingKey = pagingKey,
            database = database,
            statuses = status,
            sortIdProvider = {
                val index = status.indexOf(it)
                -(index + page * state.config.pageSize).toLong()
            },
        )

        return MediatorResult.Success(
            endOfPaginationReached = status.isEmpty(),
        )
    }
}
