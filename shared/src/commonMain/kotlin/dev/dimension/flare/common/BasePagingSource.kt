package dev.dimension.flare.common

import androidx.paging.PagingSource
import dev.dimension.flare.data.repository.DebugRepository
import kotlinx.coroutines.withContext
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public abstract class BasePagingSource<Key : Any, Value : Any> : PagingSource<Key, Value>() {
    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> =
        withContext(PlatformDispatchers.IO) {
            try {
                doLoad(params)
            } catch (e: Exception) {
                onError(e)
                DebugRepository.error(e)
                LoadResult.Error(e)
            }
        }

    public abstract suspend fun doLoad(params: LoadParams<Key>): LoadResult<Key, Value>

    protected open fun onError(e: Throwable) {
    }
}
