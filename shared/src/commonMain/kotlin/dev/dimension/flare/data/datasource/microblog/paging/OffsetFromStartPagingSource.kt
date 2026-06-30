package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job

internal data class PageInvalidationSubscription(
    val job: Job,
    val ready: Deferred<Unit>? = null,
)

internal sealed interface OffsetFromStartPagingKey {
    data class Append(
        val offset: Int,
    ) : OffsetFromStartPagingKey

    data class Refresh(
        val limit: Int,
    ) : OffsetFromStartPagingKey
}

internal interface OffsetFromStartPageLoader<Item : Any> {
    suspend fun load(
        offset: Int,
        limit: Int,
    ): List<Item>

    fun observeInvalidations(invalidate: () -> Unit): PageInvalidationSubscription? = null
}

internal class OffsetFromStartPagingSource<Item : Any>(
    private val loader: OffsetFromStartPageLoader<Item>,
) : BasePagingSource<OffsetFromStartPagingKey, Item>() {
    private var invalidationSubscription: PageInvalidationSubscription? = null

    init {
        invalidationSubscription = loader.observeInvalidations(::invalidate)
        registerInvalidatedCallback {
            invalidationSubscription?.job?.cancel()
            invalidationSubscription = null
        }
    }

    override suspend fun doLoad(params: LoadParams<OffsetFromStartPagingKey>): LoadResult<OffsetFromStartPagingKey, Item> {
        invalidationSubscription?.ready?.await()
        val offset: Int
        val limit: Int
        when (val key = params.key) {
            null -> {
                offset = 0
                limit = params.loadSize
            }

            is OffsetFromStartPagingKey.Append -> {
                offset = key.offset
                limit = params.loadSize
            }

            is OffsetFromStartPagingKey.Refresh -> {
                offset = 0
                limit = maxOf(params.loadSize, key.limit)
            }
        }

        val data = loader.load(offset = offset, limit = limit)
        return LoadResult.Page(
            data = data,
            prevKey = null,
            nextKey =
                data
                    .takeIf { it.size >= limit }
                    ?.let { OffsetFromStartPagingKey.Append(offset + it.size) },
        )
    }

    override fun getRefreshKey(state: PagingState<OffsetFromStartPagingKey, Item>): OffsetFromStartPagingKey? {
        val anchorPosition = state.anchorPosition ?: return null
        val limit =
            maxOf(
                state.config.initialLoadSize,
                anchorPosition + 1 + state.config.pageSize + state.config.prefetchDistance,
            )
        return OffsetFromStartPagingKey.Refresh(limit)
    }
}
