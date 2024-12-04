package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.bsky.feed.GetActorLikesQueryParams
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import sh.christian.ozone.api.Did

@OptIn(ExperimentalPagingApi::class)
internal class UserLikesTimelineRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.REFRESH ->
                        service
                            .getActorLikes(
                                GetActorLikesQueryParams(
                                    actor = Did(did = accountKey.id),
                                    limit = state.config.pageSize.toLong(),
                                ),
                            ).maybeResponse()

                    LoadType.PREPEND -> {
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    }

                    LoadType.APPEND -> {
                        service
                            .getActorLikes(
                                GetActorLikesQueryParams(
                                    actor = Did(did = accountKey.id),
                                    limit = state.config.pageSize.toLong(),
                                    cursor = cursor,
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
                endOfPaginationReached = cursor == null,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
