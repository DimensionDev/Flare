package dev.dimension.flare.common

import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

suspend fun <T : Any> LazyPagingItems<T>.refreshSuspend() {
    refresh()
    snapshotFlow { loadState }
        .distinctUntilChanged()
        .first {
            it.refresh is LoadState.Loading
        }
    snapshotFlow { loadState }
        .distinctUntilChanged()
        .first {
            it.refresh is LoadState.NotLoading || it.refresh is LoadState.Error
        }
}

inline fun <reified T : Any> LazyPagingItems<T>.onLoading(block: () -> Unit): LazyPagingItems<T> {
    if (loadState.refresh is LoadState.Loading && itemCount == 0) {
        block()
    }
    return this
}

val <T : Any> LazyPagingItems<T>.isLoading: Boolean
    get() = loadState.refresh is LoadState.Loading && itemCount == 0

val <T : Any> LazyPagingItems<T>.isRefreshing: Boolean
    get() = loadState.refresh is LoadState.Loading

inline fun <reified T : Any> LazyPagingItems<T>.onError(block: (Throwable) -> Unit): LazyPagingItems<T> {
    if (loadState.refresh is LoadState.Error && itemCount == 0) {
        block((loadState.refresh as LoadState.Error).error)
    }
    return this
}

val <T : Any> LazyPagingItems<T>.isError: Boolean
    get() = loadState.refresh is LoadState.Error && itemCount == 0

inline fun <reified T : Any> LazyPagingItems<T>.onEmpty(block: () -> Unit): LazyPagingItems<T> {
    if (loadState.refresh is LoadState.NotLoading && itemCount == 0) {
        block()
    }
    return this
}

val <T : Any> LazyPagingItems<T>.isEmpty: Boolean
    get() = loadState.refresh is LoadState.NotLoading && itemCount == 0

inline fun <reified T : Any> LazyPagingItems<T>.onNotEmptyOrLoading(block: () -> Unit): LazyPagingItems<T> {
    if (itemCount > 0 || loadState.refresh is LoadState.Loading) {
        block()
    }
    return this
}

val <T : Any> LazyPagingItems<T>.isNotEmptyOrLoading: Boolean
    get() = itemCount > 0 || loadState.refresh is LoadState.Loading

inline fun <reified T : Any> LazyPagingItems<T>.onSuccess(block: () -> Unit): LazyPagingItems<T> {
    if (itemCount > 0) {
        block()
    }
    return this
}

val <T : Any> LazyPagingItems<T>.isSuccess: Boolean
    get() = itemCount > 0
