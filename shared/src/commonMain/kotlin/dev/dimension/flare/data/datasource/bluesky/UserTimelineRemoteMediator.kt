package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.bsky.feed.GetAuthorFeedQueryParams
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import sh.christian.ozone.api.AtIdentifier

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val userKey: MicroBlogKey,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.REFRESH ->
                        service.getAuthorFeed(
                            GetAuthorFeedQueryParams(
                                limit = state.config.pageSize.toLong(),
                                actor = AtIdentifier(userKey.id),
                            ),
                        ).maybeResponse()

                    LoadType.PREPEND -> {
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    }

                    LoadType.APPEND -> {
                        service.getAuthorFeed(
                            GetAuthorFeedQueryParams(
                                limit = state.config.pageSize.toLong(),
                                cursor = cursor,
                                actor = AtIdentifier(userKey.id),
                            ),
                        ).maybeResponse()
                    }
                } ?: return MediatorResult.Success(
                    endOfPaginationReached = true,
                )

            cursor = response.cursor
            Bluesky.saveFeed(
                accountKey,
                pagingKey,
                database,
                response.feed,
            )

            MediatorResult.Success(
                endOfPaginationReached = response.feed.isEmpty(),
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
