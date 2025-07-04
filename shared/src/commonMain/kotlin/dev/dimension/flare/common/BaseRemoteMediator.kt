package dev.dimension.flare.common

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.repository.DebugRepository
import dev.dimension.flare.ui.model.UiTimeline

@OptIn(ExperimentalPagingApi::class)
internal abstract class BaseRemoteMediator<Key : Any, Value : Any> : RemoteMediator<Key, Value>() {
    final override suspend fun load(
        loadType: LoadType,
        state: PagingState<Key, Value>,
    ): MediatorResult =
        try {
            doLoad(loadType, state)
        } catch (e: Throwable) {
            onError(e)
            DebugRepository.error(e)
            MediatorResult.Error(e)
        }

    abstract suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Key, Value>,
    ): MediatorResult

    protected open fun onError(e: Throwable) {
    }
}

internal sealed interface BaseTimelineLoader

@OptIn(ExperimentalPagingApi::class)
internal abstract class BaseTimelineRemoteMediator(
    private val database: CacheDatabase,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>(),
    BaseTimelineLoader {
    abstract val pagingKey: String

    final override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        val result = timeline(loadType, state)
        database.connect {
            if (loadType == LoadType.REFRESH) {
                result.data.groupBy { it.timeline.pagingKey }.keys.forEach { key ->
                    database
                        .pagingTimelineDao()
                        .delete(pagingKey = key)
                }
            }
            saveToDatabase(database, result.data)
        }
        return MediatorResult.Success(
            endOfPaginationReached = result.endOfPaginationReached,
        )
    }

    abstract suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result

    data class Result(
        val endOfPaginationReached: Boolean,
        val data: List<DbPagingTimelineWithStatus> = emptyList(),
    )
}

internal abstract class BaseTimelinePagingSource<T : Any> :
    BasePagingSource<T, UiTimeline>(),
    BaseTimelineLoader

internal abstract class BasePagingSource<Key : Any, Value : Any> : PagingSource<Key, Value>() {
    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> =
        try {
            doLoad(params)
        } catch (e: Throwable) {
            onError(e)
            DebugRepository.error(e)
            LoadResult.Error(e)
        }

    abstract suspend fun doLoad(params: LoadParams<Key>): LoadResult<Key, Value>

    protected open fun onError(e: Throwable) {
    }
}
