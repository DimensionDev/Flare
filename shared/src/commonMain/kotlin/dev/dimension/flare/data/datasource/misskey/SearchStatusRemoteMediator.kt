package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesSearchRequest
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class SearchStatusRemoteMediator(
    private val service: MisskeyService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val query: String,
) : RemoteMediator<Int, DbPagingTimelineView>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineView>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.PREPEND -> {
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    }
                    LoadType.REFRESH -> {
                        service
                            .notesSearch(
                                NotesSearchRequest(
                                    query = query,
                                    limit = state.config.pageSize,
                                ),
                            ).also {
                                database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
                            }
                    }

                    LoadType.APPEND -> {
                        val lastItem =
                            state.lastItemOrNull()
                                ?: return MediatorResult.Success(
                                    endOfPaginationReached = true,
                                )
                        service.notesSearch(
                            NotesSearchRequest(
                                query = query,
                                limit = state.config.pageSize,
                                untilId = lastItem.timeline.statusKey.id,
                            ),
                        )
                    }
                }.body() ?: emptyList()

            Misskey.save(
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
