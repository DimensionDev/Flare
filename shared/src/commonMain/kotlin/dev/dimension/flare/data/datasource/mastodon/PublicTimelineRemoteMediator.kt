package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class PublicTimelineRemoteMediator(
    private val service: MastodonService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val local: Boolean,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey: String =
        buildString {
            append("public_timeline")
            if (local) {
                append("_local")
            }
            append("_$accountKey")
        }

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .publicTimeline(
                            limit = pageSize,
                            local = local,
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.publicTimeline(
                        limit = pageSize,
                        max_id = request.nextKey,
                        local = local,
                    )
                }
            }

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.next,
            previousKey = response.prev,
        )
    }
}
