package dev.dimension.flare.common

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
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

internal sealed interface BaseTimelineLoader {
    data object NotSupported : BaseTimelineLoader
}

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
        val request: Request =
            when (loadType) {
                LoadType.REFRESH -> Request.Refresh
                LoadType.PREPEND -> {
                    val previousKey =
                        database.pagingTimelineDao().getPagingKey(pagingKey)?.prevKey
                            ?: return MediatorResult.Success(endOfPaginationReached = true)
                    Request.Prepend(previousKey)
                }
                LoadType.APPEND -> {
                    val nextKey =
                        database.pagingTimelineDao().getPagingKey(pagingKey)?.nextKey
                            ?: return MediatorResult.Success(endOfPaginationReached = true)
                    Request.Append(nextKey)
                }
            }

        val result =
            timeline(
                pageSize = state.config.pageSize,
                request = request,
            )
        database.connect {
            if (loadType == LoadType.REFRESH) {
                result.data.groupBy { it.timeline.pagingKey }.keys.forEach { key ->
                    database
                        .pagingTimelineDao()
                        .delete(pagingKey = key)
                }
                database.pagingTimelineDao().deletePagingKey(pagingKey)
                database.pagingTimelineDao().insertPagingKey(
                    DbPagingKey(
                        pagingKey = pagingKey,
                        nextKey = result.nextKey,
                        prevKey = result.previousKey,
                    ),
                )
            } else if (loadType == LoadType.PREPEND && result.previousKey != null) {
                database.pagingTimelineDao().updatePagingKeyPrevKey(
                    pagingKey = pagingKey,
                    prevKey = result.previousKey,
                )
            } else if (loadType == LoadType.APPEND && result.nextKey != null) {
                database.pagingTimelineDao().updatePagingKeyNextKey(
                    pagingKey = pagingKey,
                    nextKey = result.nextKey,
                )
            }
            saveToDatabase(database, result.data)
        }
        return MediatorResult.Success(
            endOfPaginationReached =
                result.endOfPaginationReached ||
                    when (loadType) {
                        LoadType.REFRESH -> false
                        LoadType.PREPEND -> result.previousKey == null
                        LoadType.APPEND -> result.nextKey == null
                    },
        )
    }

    abstract suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result

    data class Result(
        val endOfPaginationReached: Boolean,
        val data: List<DbPagingTimelineWithStatus> = emptyList(),
        val nextKey: String? = null,
        val previousKey: String? = null,
    )

    sealed interface Request {
        data object Refresh : Request

        data class Prepend(
            val previousKey: String,
        ) : Request

        data class Append(
            val nextKey: String,
        ) : Request
    }
}

internal fun interface BaseTimelinePagingSourceFactory<T : Any> : BaseTimelineLoader {
    abstract fun create(): BasePagingSource<T, UiTimeline>
}

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
