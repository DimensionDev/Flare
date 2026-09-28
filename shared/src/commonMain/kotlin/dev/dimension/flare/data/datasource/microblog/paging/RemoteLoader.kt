package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.stableItemKey
import dev.dimension.flare.ui.model.withItemKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface RemoteLoader<T : Any> {
    public suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<T>
}

@HiddenFromObjC
public fun <T : Any> notSupported(): RemoteLoader<T> = NotSupportRemoteLoader()

internal class NotSupportRemoteLoader<T : Any> : RemoteLoader<T> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<T> = PagingResult(endOfPaginationReached = true)
}

@HiddenFromObjC
public fun RemoteLoader<UiTimelineV2>.toTimelinePagingSource(): PagingSource<String, UiTimelineV2> {
    // ponytail: pagingConfig retains all pages; use page-scoped identities if page dropping is enabled.
    val seenKeys = mutableSetOf<String>()
    val mutex = Mutex()
    return object : RemoteLoader<UiTimelineV2> {
        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            val result = this@toTimelinePagingSource.load(pageSize, request)
            return mutex.withLock {
                if (request == PagingRequest.Refresh) seenKeys.clear()
                result.copy(
                    data =
                        result.data.mapNotNull { item ->
                            val key = item.stableItemKey
                            if (seenKeys.add(key)) item.withItemKey(key) else null
                        },
                )
            }
        }
    }.toPagingSource()
}

@HiddenFromObjC
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
