package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.UsersNotesRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val account: UiAccount.Misskey,
    private val service: MisskeyService,
    private val userKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val onlyMedia: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
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
                            state.lastItemOrNull()
                                ?: return MediatorResult.Success(
                                    endOfPaginationReached = true,
                                )
                        service.usersNotes(
                            UsersNotesRequest(
                                userId = userKey.id,
                                limit = state.config.pageSize,
                                untilId = lastItem.timeline_status_key.id,
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
                }.body() ?: return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            if (loadType == LoadType.REFRESH) {
                database.dbPagingTimelineQueries.deletePaging(account.accountKey, pagingKey)
            }

            Misskey.save(
                database = database,
                accountKey = account.accountKey,
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
