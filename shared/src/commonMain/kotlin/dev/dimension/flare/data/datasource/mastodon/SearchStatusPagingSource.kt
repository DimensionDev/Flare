package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusPagingSource(
    private val service: MastodonService,
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

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                Request.Refresh -> {
                    if (query.startsWith("#")) {
                        service.hashtagTimeline(
                            hashtag = query.removePrefix("#"),
                            limit = pageSize,
                        )
                    } else {
                        service
                            .searchV2(
                                query = query,
                                limit = pageSize,
                                type = "statuses",
                            ).statuses
                    }
                }

                is Request.Append -> {
                    if (query.startsWith("#")) {
                        service.hashtagTimeline(
                            hashtag = query.removePrefix("#"),
                            limit = pageSize,
                            max_id = request.nextKey,
                        )
                    } else {
                        service
                            .searchV2(
                                query = query,
                                limit = pageSize,
                                max_id = request.nextKey,
                                type = "statuses",
                            ).statuses
                    }
                }
            } ?: emptyList()

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.lastOrNull()?.id,
        )
    }
}
