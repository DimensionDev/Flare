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
import dev.dimension.flare.model.vvo

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val mediaOnly: Boolean,
) : BaseTimelineRemoteMediator(
        database = database,
        clearWhenRefresh = true,
        pagingKey = pagingKey,
        accountType = AccountType.Specific(accountKey),
    ) {
    private var containerid: String? = null
    var page = 0

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        if (mediaOnly) {
            // Not supported yet
            return Result(
                endOfPaginationReached = true,
            )
        }

        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException
        }
        if (containerid == null) {
            containerid =
                service
                    .getContainerIndex(type = "uid", value = userKey.id)
                    .data
                    ?.tabsInfo
                    ?.tabs
                    ?.firstOrNull {
                        it.tabType == vvo
                    }?.containerid
        }
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    page = 0
                    service
                        .getContainerIndex(
                            type = "uid",
                            value = userKey.id,
                            containerId = containerid,
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    page++
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    service.getContainerIndex(
                        type = "uid",
                        value = userKey.id,
                        containerId = containerid,
                        sinceId = lastItem.timeline.statusKey.id,
                    )
                }
            }
        val status =
            response.data
                ?.cards
                ?.mapNotNull { it.mblog }
                .orEmpty()

        val data =
            status.map { statusItem ->
                statusItem.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = {
                        val index = status.indexOf(it)
                        -(index + page * state.config.pageSize).toLong()
                    },
                )
            }

        return Result(
            endOfPaginationReached = response.data?.cardlistInfo?.sinceID == null,
            data = data,
        )
    }
}
