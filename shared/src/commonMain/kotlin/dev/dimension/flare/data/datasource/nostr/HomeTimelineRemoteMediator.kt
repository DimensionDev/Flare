package dev.dimension.flare.data.datasource.nostr

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.model.MicroBlogKey

private const val DEFAULT_RELAY = "wss://nos.lol"

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: NostrService,
    database: CacheDatabase,
    private val relay: String = DEFAULT_RELAY,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "nostr_home_$accountKey"

    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val until =
            when (request) {
                is Request.Prepend -> return Result(
                    endOfPaginationReached = true,
                )
                Request.Refresh -> null
                is Request.Append -> request.nextKey.toLongOrNull()
            }

        val events =
            service.homeTimeline(
                relay = relay,
                limit = pageSize,
                until = until,
            )

        return Result(
            endOfPaginationReached = events.isEmpty(),
            data =
                events.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = events.lastOrNull()?.createdAt?.toString(),
        )
    }
}
