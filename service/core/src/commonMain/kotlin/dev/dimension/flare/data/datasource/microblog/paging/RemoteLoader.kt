package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState

public interface RemoteLoader<T : Any> {
    public suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<T>
}

public fun <T : Any> notSupported(): RemoteLoader<T> = NotSupportRemoteLoader()

public class NotSupportRemoteLoader<T : Any> : RemoteLoader<T> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<T> = PagingResult(endOfPaginationReached = true)
}

public fun <T : Any> RemoteLoader<T>.toPagingSource(): PagingSource<String, T> =
    object : PagingSource<String, T>() {
        override suspend fun load(params: LoadParams<String>): LoadResult<String, T> {
            val request =
                when (params) {
                    is LoadParams.Refresh -> PagingRequest.Refresh
                    is LoadParams.Prepend -> PagingRequest.Prepend(previousKey = params.key)
                    is LoadParams.Append -> PagingRequest.Append(nextKey = params.key)
                }
            return try {
                val result =
                    load(
                        pageSize = params.loadSize,
                        request = request,
                    )
                LoadResult.Page(
                    data = result.data,
                    prevKey = result.previousKey,
                    nextKey = result.nextKey,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<String, T>): String? = null
    }
