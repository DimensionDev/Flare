package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
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
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }

        val page =
            when (request) {
                is PagingRequest.Append -> request.nextKey.toIntOrNull() ?: 1
                is PagingRequest.Prepend -> 1
                PagingRequest.Refresh -> 1
            }

        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .getContainerIndex(
                            containerId = containerId,
                            pageType = "searchall",
                        )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
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

        return PagingResult(
            endOfPaginationReached = status.isEmpty(),
            data = data,
            nextKey = (page + 1).toString(),
        )
    }
}
