package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val service: MastodonService,
    private val database: CacheDatabase,
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
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    val pinned =
                        if (withPinned) {
                            service.userTimeline(
                                user_id = userKey.id,
                                limit = state.config.pageSize,
                                pinned = true,
                            )
                        } else {
                            emptyList()
                        }
                    service
                        .userTimeline(
                            user_id = userKey.id,
                            limit = state.config.pageSize,
                            only_media = onlyMedia,
                            exclude_replies = !withReplies,
                        ) + pinned
                }

                LoadType.PREPEND -> {
                    val firstItem = state.firstItemOrNull()
                    service.userTimeline(
                        user_id = userKey.id,
                        limit = state.config.pageSize,
                        min_id = firstItem?.timeline?.statusKey?.id,
                        only_media = onlyMedia,
                        exclude_replies = !withReplies,
                    )
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return Result(
                                endOfPaginationReached = true,
                            )
                    service.userTimeline(
                        user_id = userKey.id,
                        limit = state.config.pageSize,
                        max_id = lastItem.timeline.statusKey.id,
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
        )
    }
}
