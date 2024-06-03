package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val mediaOnly: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    private var containerid: String? = null
    var page = 0

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        if (mediaOnly) {
            // Not supported yet
            return MediatorResult.Success(
                endOfPaginationReached = true,
            )
        }
        return try {
            val config = service.config()
            if (config.data?.login != true) {
                return MediatorResult.Error(
                    LoginExpiredException,
                )
            }
            if (containerid == null) {
                containerid =
                    service.getContainerIndex(type = "uid", value = userKey.id)
                        .data
                        ?.tabsInfo
                        ?.tabs
                        ?.firstOrNull {
                            it.tabType == "weibo"
                        }
                        ?.containerid
            }
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        page = 0
                        service.getContainerIndex(
                            type = "uid",
                            value = userKey.id,
                            containerId = containerid,
                        ).also {
                            database.transaction {
                                database.dbPagingTimelineQueries.deletePaging(accountKey, pagingKey)
                            }
                        }
                    }

                    LoadType.PREPEND -> {
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    }

                    LoadType.APPEND -> {
                        page++
                        val lastItem =
                            state.lastItemOrNull()
                                ?: return MediatorResult.Success(
                                    endOfPaginationReached = true,
                                )
                        service.getContainerIndex(
                            type = "uid",
                            value = userKey.id,
                            containerId = containerid,
                            sinceId = lastItem.timeline_status_key.id,
                        )
                    }
                }
            val status = response.data?.cards?.mapNotNull { it.mblog }.orEmpty()

            VVO.save(
                accountKey = accountKey,
                pagingKey = pagingKey,
                database = database,
                statuses = status,
                sortIdProvider = {
                    val index = status.indexOf(it)
                    -(index + page * state.config.pageSize).toLong()
                },
            )
            MediatorResult.Success(
                endOfPaginationReached = response.data?.cardlistInfo?.sinceID == null,
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            MediatorResult.Error(e)
        }
    }
}
