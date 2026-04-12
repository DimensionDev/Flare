package dev.dimension.flare.common

import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal suspend fun <T : Any> LazyPagingItems<T>.refreshSuspend() {
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

internal inline fun <reified T : Any> LazyPagingItems<T>.onLoading(block: () -> Unit): LazyPagingItems<T> {
    if (itemCount == 0 && !snapshot().isResolvedEmpty() && snapshot().initialErrorOrNull() == null) {
        block()
    }
    return this
}

internal val <T : Any> LazyPagingItems<T>.isLoading: Boolean
    get() = itemCount == 0 && !snapshot().isResolvedEmpty() && snapshot().initialErrorOrNull() == null

internal val <T : Any> LazyPagingItems<T>.isRefreshing: Boolean
    get() = loadState.refresh is LoadState.Loading

internal inline fun <reified T : Any> LazyPagingItems<T>.onError(block: (Throwable) -> Unit): LazyPagingItems<T> {
    snapshot().initialErrorOrNull()?.takeIf { itemCount == 0 }?.let {
        block(it)
    }
    return this
}

internal val <T : Any> LazyPagingItems<T>.isError: Boolean
    get() = itemCount == 0 && snapshot().initialErrorOrNull() != null

internal inline fun <reified T : Any> LazyPagingItems<T>.onEmpty(block: () -> Unit): LazyPagingItems<T> {
    if (snapshot().isResolvedEmpty()) {
        block()
    }
    return this
}

internal val <T : Any> LazyPagingItems<T>.isEmpty: Boolean
    get() = snapshot().isResolvedEmpty()

internal inline fun <reified T : Any> LazyPagingItems<T>.onNotEmptyOrLoading(block: () -> Unit): LazyPagingItems<T> {
    if (itemCount > 0 || isLoading) {
        block()
    }
    return this
}

internal val <T : Any> LazyPagingItems<T>.isNotEmptyOrLoading: Boolean
    get() = itemCount > 0 || isLoading

internal inline fun <reified T : Any> LazyPagingItems<T>.onSuccess(block: () -> Unit): LazyPagingItems<T> {
    if (itemCount > 0) {
        block()
    }
    return this
}

internal val <T : Any> LazyPagingItems<T>.isSuccess: Boolean
    get() = itemCount > 0
