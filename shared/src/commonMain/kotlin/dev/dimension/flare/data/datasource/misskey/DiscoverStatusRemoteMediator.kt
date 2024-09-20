package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.NotesFeaturedRequest
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class DiscoverStatusRemoteMediator(
    private val service: MisskeyService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineView>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineView>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        service.notesFeatured(NotesFeaturedRequest(limit = state.config.initialLoadSize))
                    }
                    LoadType.APPEND -> {
                        service.notesFeatured(
                            NotesFeaturedRequest(
                                limit = state.config.pageSize,
                                untilId =
                                    state
                                        .lastItemOrNull()
                                        ?.timeline
                                        ?.statusKey
                                        ?.id,
                            ),
                        )
                    }
                    else -> {
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
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
                endOfPaginationReached = true,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
