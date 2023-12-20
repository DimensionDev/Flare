package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull

suspend fun <T : Any> LazyPagingItems<T>.refreshSuspend() {
    refresh()
    snapshotFlow { loadState }
        .distinctUntilChanged()
        .filter {
            it.refresh is LoadState.NotLoading
        }
        .firstOrNull()
}

@Composable
fun <T : Any> Flow<PagingData<T>>.collectPagingProxy(): LazyPagingItemsProxy<T> {
    val data = collectAsLazyPagingItems()
    return LazyPagingItemsProxy(
        data = data,
        itemCountInternal = data.itemCount,
        loadStateInternal = data.loadState,
    )
}

inline fun <reified T : Any> LazyPagingItemsProxy<T>.onLoading(block: () -> Unit): LazyPagingItemsProxy<T> {
    if (loadState.refresh is LoadState.Loading && itemCount == 0) {
        block()
    }
    return this
}

val <T : Any> LazyPagingItemsProxy<T>.isLoading: Boolean
    get() = loadState.refresh is LoadState.Loading && itemCount == 0

inline fun <reified T : Any> LazyPagingItemsProxy<T>.onError(block: (Throwable) -> Unit): LazyPagingItemsProxy<T> {
    if (loadState.refresh is LoadState.Error && itemCount == 0) {
        block((loadState.refresh as LoadState.Error).error)
    }
    return this
}

val <T : Any> LazyPagingItemsProxy<T>.isError: Boolean
    get() = loadState.refresh is LoadState.Error && itemCount == 0

inline fun <reified T : Any> LazyPagingItemsProxy<T>.onEmpty(block: () -> Unit): LazyPagingItemsProxy<T> {
    if (loadState.refresh is LoadState.NotLoading && itemCount == 0) {
        block()
    }
    return this
}

val <T : Any> LazyPagingItemsProxy<T>.isEmpty: Boolean
    get() = loadState.refresh is LoadState.NotLoading && itemCount == 0

inline fun <reified T : Any> LazyPagingItemsProxy<T>.onNotEmptyOrLoading(block: () -> Unit): LazyPagingItemsProxy<T> {
    if (itemCount > 0 || loadState.refresh is LoadState.Loading) {
        block()
    }
    return this
}

val <T : Any> LazyPagingItemsProxy<T>.isNotEmptyOrLoading: Boolean
    get() = itemCount > 0 || loadState.refresh is LoadState.Loading

inline fun <reified T : Any> LazyPagingItemsProxy<T>.onSuccess(block: () -> Unit): LazyPagingItemsProxy<T> {
    if (itemCount > 0) {
        block()
    }
    return this
}

val <T : Any> LazyPagingItemsProxy<T>.isSuccess: Boolean
    get() = itemCount > 0

// for iOS, collectAsLazyPagingItems does not trigger PresenterBase to emit new state when LazyPagingItems changes itself
@Immutable
data class LazyPagingItemsProxy<T : Any>(
    val data: LazyPagingItems<T>,
    private val itemCountInternal: Int = data.itemCount,
    private val loadStateInternal: CombinedLoadStates = data.loadState,
) {
    val itemCount: Int
        get() = data.itemCount
    val loadState: CombinedLoadStates
        get() = data.loadState

    operator fun get(index: Int): T? = data.get(index)

    fun peek(index: Int): T? = if (index < itemCount) data.peek(index) else null

    fun retry() = data.retry()

    fun refresh() = data.refresh()

    suspend fun refreshSuspend() = data.refreshSuspend()

    fun itemKey(key: ((item: T) -> Any)? = null): (index: Int) -> Any = data.itemKey(key = key)

    fun itemContentType(contentType: ((item: T) -> Any?)?): (index: Int) -> Any? = data.itemContentType(contentType)
}
