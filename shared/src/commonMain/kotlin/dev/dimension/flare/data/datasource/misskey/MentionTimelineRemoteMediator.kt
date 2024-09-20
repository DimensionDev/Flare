package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesMentionsRequest
import dev.dimension.flare.ui.model.UiAccount

@OptIn(ExperimentalPagingApi::class)
internal class MentionTimelineRemoteMediator(
    private val account: UiAccount.Misskey,
    private val service: MisskeyService,
    private val database: CacheDatabase,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineView>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineView>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.PREPEND -> return MediatorResult.Success(
                        endOfPaginationReached = true,
                    )
                    LoadType.REFRESH -> {
                        service.notesMentions(
                            NotesMentionsRequest(
                                limit = state.config.pageSize,
                            ),
                        )
                    }

                    LoadType.APPEND -> {
                        val lastItem =
                            state.lastItemOrNull()
                                ?: return MediatorResult.Success(
                                    endOfPaginationReached = true,
                                )
                        service.notesMentions(
                            NotesMentionsRequest(
                                limit = state.config.pageSize,
                                untilId = lastItem.timeline.statusKey.id,
                            ),
                        )
                    }
                }.body() ?: return MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            if (loadType == LoadType.REFRESH) {
                database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = account.accountKey)
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
