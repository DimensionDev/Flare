package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val service: MastodonService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val onlyMedia: Boolean = false,
    private val withReplies: Boolean = false,
    private val withPinned: Boolean = false,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey =
        buildString {
            append("user_timeline")
            if (onlyMedia) {
                append("media")
            }
            if (withReplies) {
                append("replies")
            }
            if (withPinned) {
                append("pinned")
            }
            append(accountKey.toString())
            append(userKey.toString())
        }

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    val pinned =
                        if (withPinned) {
                            service.userTimeline(
                                user_id = userKey.id,
                                limit = pageSize,
                                pinned = true,
                            )
                        } else {
                            emptyList()
                        }
                    service
                        .userTimeline(
                            user_id = userKey.id,
                            limit = pageSize,
                            only_media = onlyMedia,
                            exclude_replies = !withReplies,
                        ) + pinned
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service.userTimeline(
                        user_id = userKey.id,
                        limit = pageSize,
                        max_id = request.nextKey,
                        only_media = onlyMedia,
                        exclude_replies = !withReplies,
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
