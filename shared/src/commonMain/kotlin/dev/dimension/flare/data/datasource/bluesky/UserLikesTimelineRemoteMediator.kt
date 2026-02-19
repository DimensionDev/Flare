package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.GetActorLikesQueryParams
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import sh.christian.ozone.api.Did

@OptIn(ExperimentalPagingApi::class)
internal class UserLikesTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "user_timeline_likes_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val response =
            when (request) {
                PagingRequest.Refresh ->
                    service
                        .getActorLikes(
                            GetActorLikesQueryParams(
                                actor = Did(did = accountKey.id),
                                limit = pageSize.toLong(),
                            ),
                        ).maybeResponse()

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service
                        .getActorLikes(
                            GetActorLikesQueryParams(
                                actor = Did(did = accountKey.id),
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).maybeResponse()
                }
            } ?: return PagingResult(
                endOfPaginationReached = true,
            )

        return PagingResult(
            endOfPaginationReached = response.cursor == null,
            data =
                response.feed.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.cursor,
        )
    }
}
