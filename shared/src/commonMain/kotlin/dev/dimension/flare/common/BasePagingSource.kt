package dev.dimension.flare.common

import androidx.paging.PagingSource
import dev.dimension.flare.data.repository.DebugRepository

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
