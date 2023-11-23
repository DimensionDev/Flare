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
        itemCount = data.itemCount,
        loadState = data.loadState,
    )
}

// for iOS, collectAsLazyPagingItems does not trigger PresenterBase to emit new state when LazyPagingItems changes itself
@Immutable
data class LazyPagingItemsProxy<T : Any>(
    val data: LazyPagingItems<T>,
    val itemCount: Int = data.itemCount,
    val loadState: CombinedLoadStates = data.loadState,
) {
    operator fun get(index: Int): T? = data.get(index)

    fun peek(index: Int): T? = data.peek(index)

    fun retry() = data.retry()

    fun refresh() = data.refresh()

    suspend fun refreshSuspend() = data.refreshSuspend()

    fun itemKey(key: ((item: T) -> Any)? = null): (index: Int) -> Any = data.itemKey(key = key)

    fun itemContentType(contentType: ((item: T) -> Any?)?): (index: Int) -> Any? = data.itemContentType(contentType)
}
