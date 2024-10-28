package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
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
    private val onlyMedia: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.REFRESH ->
                        service.userTimeline(
                            user_id = userKey.id,
                            limit = state.config.pageSize,
                            only_media = onlyMedia,
                        )

                    LoadType.PREPEND -> {
                        val firstItem = state.firstItemOrNull()
                        service.userTimeline(
                            user_id = userKey.id,
                            limit = state.config.pageSize,
                            min_id = firstItem?.timeline?.statusKey?.id,
                            only_media = onlyMedia,
                        )
                    }

                    LoadType.APPEND -> {
                        val lastItem =
                            state.lastItemOrNull()
                                ?: return MediatorResult.Success(
                                    endOfPaginationReached = true,
                                )
                        service.userTimeline(
                            user_id = userKey.id,
                            limit = state.config.pageSize,
                            max_id = lastItem.timeline.statusKey.id,
                            only_media = onlyMedia,
                        )
                    }
                }

            Mastodon.save(
                database = database,
                accountKey = accountKey,
                pagingKey = pagingKey,
                data = response,
            )

            MediatorResult.Success(
                endOfPaginationReached = response.isEmpty(),
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
