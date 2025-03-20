package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Mastodon
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val service: MastodonService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
    private val pagingKey: String,
    private val onlyMedia: Boolean = false,
    private val withReplies: Boolean = false,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        val response =
            when (loadType) {
                LoadType.REFRESH ->
                    service.userTimeline(
                        user_id = userKey.id,
                        limit = state.config.pageSize,
                        only_media = onlyMedia,
                        exclude_replies = !withReplies,
                    )

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
                            ?: return MediatorResult.Success(
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

        Mastodon.save(
            database = database,
            accountKey = accountKey,
            pagingKey = pagingKey,
            data = response,
        )

        return MediatorResult.Success(
            endOfPaginationReached = response.isEmpty(),
        )
    }
}
