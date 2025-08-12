package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: VVOService,
    database: CacheDatabase,
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

    private val containerId by lazy {
        "100103type=1&q=$query&t="
    }

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

        val page =
            when (request) {
                is Request.Append -> request.nextKey.toIntOrNull() ?: 1
                is Request.Prepend -> 1
                Request.Refresh -> 1
            }

        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getContainerIndex(
                            containerId = containerId,
                            pageType = "searchall",
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
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
                        -(index + page * pageSize).toLong()
                    },
                )
            }

        return Result(
            endOfPaginationReached = status.isEmpty(),
            data = data,
            nextKey = (page + 1).toString(),
        )
    }
}
