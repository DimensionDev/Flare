package dev.dimension.flare.common

import androidx.paging.PagingSource
import dev.dimension.flare.common.DebugRepository

public abstract class BasePagingSource<Key : Any, Value : Any> : PagingSource<Key, Value>() {
    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> =
        try {
            doLoad(params)
        } catch (e: Exception) {
            onError(e)
            DebugRepository.error(e)
            LoadResult.Error(e)
        }

    public abstract suspend fun doLoad(params: LoadParams<Key>): LoadResult<Key, Value>

    protected open fun onError(e: Throwable) {
    }
}
