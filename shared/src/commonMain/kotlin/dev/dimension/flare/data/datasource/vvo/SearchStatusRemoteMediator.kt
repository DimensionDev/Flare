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

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: VVOService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val query: String,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String =
        buildString {
            append("search_")
            append(query)
            append(accountKey.toString())
        }

    private var page = 1
    private val containerId by lazy {
        "100103type=1&q=$query&t="
    }

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
                    page = 1
                    service
                        .getContainerIndex(
                            containerId = containerId,
                            pageType = "searchall",
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
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
                ?.flatMap { card -> listOfNotNull(card.mblog) + card.cardGroup?.mapNotNull { it.mblog }.orEmpty() }
                .orEmpty()

        val data =
            status.map { statusItem ->
                statusItem.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    sortIdProvider = { item ->
                        val index = status.indexOf(item)
                        -(index + page * state.config.pageSize).toLong()
                    },
                )
            }

        return Result(
            endOfPaginationReached = status.isEmpty(),
            data = data,
        )
    }
}
