package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import app.bsky.feed.GetAuthorFeedFilter
import app.bsky.feed.GetAuthorFeedQueryParams
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import sh.christian.ozone.api.Did

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val userKey: MicroBlogKey,
    private val onlyMedia: Boolean = false,
    private val withReplies: Boolean = false,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    var cursor: String? = null
    override val pagingKey =
        buildString {
            append("user_timeline")
            if (onlyMedia) {
                append("media")
            }
            if (withReplies) {
                append("replies")
            }
            append(accountKey.toString())
            append(userKey.toString())
        }

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val filter =
            when {
                onlyMedia -> GetAuthorFeedFilter.PostsWithMedia
                withReplies -> GetAuthorFeedFilter.PostsWithReplies
                else -> GetAuthorFeedFilter.PostsAndAuthorThreads
            }
        val response =
            when (loadType) {
                LoadType.REFRESH ->
                    service
                        .getAuthorFeed(
                            GetAuthorFeedQueryParams(
                                limit = state.config.pageSize.toLong(),
                                actor = Did(did = userKey.id),
                                filter = filter,
                                includePins = true,
                            ),
                        ).maybeResponse()

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    service
                        .getAuthorFeed(
                            GetAuthorFeedQueryParams(
                                limit = state.config.pageSize.toLong(),
                                cursor = cursor,
                                actor = Did(did = userKey.id),
                                filter = filter,
                                includePins = true,
                            ),
                        ).maybeResponse()
                }
            } ?: return Result(
                endOfPaginationReached = true,
            )

        cursor = response.cursor
        return Result(
            endOfPaginationReached = cursor == null,
            data =
                response.feed.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
        )
    }
}
