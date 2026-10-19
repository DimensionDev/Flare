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
    enum class Direction { Around, Before, After }

    data class Window(
        val itemKey: String,
        val sortId: Long,
        val direction: Direction = Direction.Around,
    ) : OffsetFromStartPagingKey

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

    fun windowKey(item: Item): OffsetFromStartPagingKey.Window? = null

    suspend fun windowOffset(key: OffsetFromStartPagingKey.Window): Int = 0

    fun observeInvalidations(invalidate: () -> Unit): PageInvalidationSubscription? = null
}

internal class OffsetFromStartPagingSource<Item : Any>(
    private val loader: OffsetFromStartPageLoader<Item>,
    private val anchor: (() -> OffsetFromStartPagingKey.Window?)? = null,
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
        val key = if (params is LoadParams.Refresh) anchor?.invoke() ?: params.key else params.key
        if (key is OffsetFromStartPagingKey.Window) {
            val index = loader.windowOffset(key)
            val offset =
                when (key.direction) {
                    OffsetFromStartPagingKey.Direction.Around -> (index - params.loadSize / 2).coerceAtLeast(0)
                    OffsetFromStartPagingKey.Direction.Before -> (index - params.loadSize).coerceAtLeast(0)
                    OffsetFromStartPagingKey.Direction.After -> index + 1
                }
            val limit = if (key.direction == OffsetFromStartPagingKey.Direction.Before) index - offset else params.loadSize
            val data = loader.load(offset, limit)
            return windowPage(data, offset, limit)
        }
        val offset: Int
        val limit: Int
        when (key) {
            is OffsetFromStartPagingKey.Window -> {
                error("Handled above")
            }

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
        if (anchor != null) return windowPage(data, offset, limit)
        return LoadResult.Page(
            data = data,
            prevKey = null,
            nextKey =
                data
                    .takeIf { it.size >= limit }
                    ?.let { OffsetFromStartPagingKey.Append(offset + it.size) },
        )
    }

    private fun windowPage(
        data: List<Item>,
        offset: Int,
        limit: Int,
    ): LoadResult<OffsetFromStartPagingKey, Item> =
        LoadResult.Page(
            data = data,
            prevKey =
                if (offset >
                    0
                ) {
                    data.firstOrNull()?.let(loader::windowKey)?.copy(direction = OffsetFromStartPagingKey.Direction.Before)
                } else {
                    null
                },
            nextKey =
                if (limit > 0 &&
                    data.size >= limit
                ) {
                    data.lastOrNull()?.let(loader::windowKey)?.copy(direction = OffsetFromStartPagingKey.Direction.After)
                } else {
                    null
                },
        )

    override fun getRefreshKey(state: PagingState<OffsetFromStartPagingKey, Item>): OffsetFromStartPagingKey? {
        if (anchor != null) {
            return anchor.invoke() ?: state.anchorPosition?.let(state::closestItemToPosition)?.let(loader::windowKey)
        }
        val anchorPosition = state.anchorPosition ?: return null
        val limit =
            maxOf(
                state.config.initialLoadSize,
                anchorPosition + 1 + state.config.pageSize + state.config.prefetchDistance,
            )
        return OffsetFromStartPagingKey.Refresh(limit)
    }
}
