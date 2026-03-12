package dev.dimension.flare.common

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.data.repository.DebugRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPagingApi::class)
internal abstract class BaseRemoteMediator<Key : Any, Value : Any> : RemoteMediator<Key, Value>() {
    final override suspend fun load(
        loadType: LoadType,
        state: PagingState<Key, Value>,
    ): MediatorResult =
        withContext(Dispatchers.IO) {
            try {
                doLoad(loadType, state)
            } catch (e: Throwable) {
                onError(e)
                DebugRepository.error(e)
                MediatorResult.Error(e)
            }
        }

    abstract suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Key, Value>,
    ): MediatorResult

    protected open fun onError(e: Throwable) {
    }
}
