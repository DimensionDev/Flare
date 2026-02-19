package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingKey

internal fun <T : Any, R : Any> createPagingRemoteMediator(
    database: CacheDatabase,
    pagingKey: String,
    onLoad: suspend (pageSize: Int, request: PagingRequest) -> PagingResult<R>,
    onSave: suspend (request: PagingRequest, data: List<R>) -> Unit,
): BasePagingRemoteMediator<T, R> =
    object : BasePagingRemoteMediator<T, R>(database) {
        override val pagingKey: String = pagingKey

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<R> = onLoad(pageSize, request)

        override suspend fun onSaveCache(
            request: PagingRequest,
            data: List<R>,
        ) {
            onSave(request, data)
        }
    }

internal abstract class BasePagingRemoteMediator<T : Any, R : Any>(
    private val database: CacheDatabase,
) : BaseRemoteMediator<Int, T>() {
    abstract val pagingKey: String

    @OptIn(ExperimentalPagingApi::class)
    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, T>,
    ): MediatorResult {
        val request: PagingRequest =
            when (loadType) {
                LoadType.REFRESH -> PagingRequest.Refresh
                LoadType.PREPEND -> {
                    val previousKey =
                        database.pagingTimelineDao().getPagingKey(pagingKey)?.prevKey
                            ?: return MediatorResult.Success(endOfPaginationReached = true)
                    PagingRequest.Prepend(previousKey)
                }
                LoadType.APPEND -> {
                    val nextKey =
                        database.pagingTimelineDao().getPagingKey(pagingKey)?.nextKey
                            ?: return MediatorResult.Success(endOfPaginationReached = true)
                    PagingRequest.Append(nextKey)
                }
            }

        val result =
            load(
                pageSize = state.config.pageSize,
                request = request,
            )
        database.connect {
            if (loadType == LoadType.REFRESH) {
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
            onSaveCache(request, result.data)
        }
        return MediatorResult.Success(
            endOfPaginationReached =
                when (loadType) {
                    LoadType.REFRESH -> false
                    LoadType.PREPEND -> result.previousKey == null
                    LoadType.APPEND -> result.nextKey == null
                },
        )
    }

    protected abstract suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<R>

    protected abstract suspend fun onSaveCache(
        request: PagingRequest,
        data: List<R>,
    )
}
