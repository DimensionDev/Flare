package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersNotesRequest
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val accountKey: MicroBlogKey,
    private val service: MisskeyService,
    private val userKey: MicroBlogKey,
    private val database: CacheDatabase,
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
                LoadType.PREPEND -> return MediatorResult.Success(
                    endOfPaginationReached = true,
                )

                LoadType.REFRESH -> {
                    service.usersNotes(
                        UsersNotesRequest(
                            userId = userKey.id,
                            limit = state.config.pageSize,
                            withReplies = withReplies,
                        ).let {
                            if (onlyMedia) {
                                it.copy(
                                    withFiles = true,
                                    withRenotes = false,
                                    withReplies = false,
                                    withChannelNotes = true,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return MediatorResult.Success(
                                endOfPaginationReached = true,
                            )
                    service.usersNotes(
                        UsersNotesRequest(
                            userId = userKey.id,
                            limit = state.config.pageSize,
                            untilId = lastItem.timeline.statusKey.id,
                            withReplies = withReplies,
                        ).let {
                            if (onlyMedia) {
                                it.copy(
                                    withFiles = true,
                                    withRenotes = false,
                                    withReplies = false,
                                    withChannelNotes = true,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
            } ?: return MediatorResult.Success(
                endOfPaginationReached = true,
            )
        if (loadType == LoadType.REFRESH) {
            database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
        }

        Misskey.save(
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
