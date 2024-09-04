package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Immutable
sealed class PagingState<T> {
    @Immutable
    class Loading<T> internal constructor() : PagingState<T>()

    @Immutable
    data class Error<T> internal constructor(
        val error: Throwable,
    ) : PagingState<T>()

    @Immutable
    data class Empty<T : Any> internal constructor(
        private val onRefresh: () -> Unit,
    ) : PagingState<T>() {
        fun refresh() {
            onRefresh()
        }
    }

    @Immutable
    sealed class Success<T: Any> : PagingState<T>() {
        abstract val itemCount: Int
        abstract val isRefreshing: Boolean
        abstract val appendState: LoadState
        abstract operator fun get(index: Int): T?
        abstract fun peek(index: Int): T?
        abstract suspend fun refreshSuspend()
        abstract fun retry()
        abstract fun itemKey(key: ((item: T) -> Any)? = null): (index: Int) -> Any
        abstract fun itemContentType(contentType: ((item: T) -> Any?)? = null): (index: Int) -> Any?

        @Immutable
        internal data class SingleSuccess<T: Any>(
            private val data: CacheableState<ImmutableList<T>>,
        ) : Success<T>() {
            override val itemCount: Int
                get() = data.data?.size ?: 0
            override val isRefreshing: Boolean
                get() = data.refreshState is LoadState.Loading

            override fun get(index: Int): T? {
                return data.data?.getOrNull(index)
            }

            override fun peek(index: Int): T? {
                return data.data?.getOrNull(index)
            }

            override suspend fun refreshSuspend() {
                data.refresh()
            }

            override fun retry() {
                data.refresh()
            }

            override val appendState: LoadState
                get() = LoadState.NotLoading(true)

            override fun itemContentType(contentType: ((item: T) -> Any?)?): (index: Int) -> Any? {
                return { null }
            }

            override fun itemKey(key: ((item: T) -> Any)?): (index: Int) -> Any {
                return { it }
            }
        }


        @Immutable
        internal data class PagingSuccess<T : Any>(
            private val data: LazyPagingItems<T>,
            override val appendState: LoadState,
        ) : Success<T>() {
            override val itemCount: Int
                get() = data.itemCount
            override val isRefreshing: Boolean
                get() = data.isRefreshing

            override operator fun get(index: Int): T? = data[index]

            override fun peek(index: Int): T? = data.peek(index)

            override suspend fun refreshSuspend() {
                data.refreshSuspend()
            }

            override fun retry() {
                data.retry()
            }

            override fun itemKey(key: ((item: T) -> Any)?): (index: Int) -> Any = data.itemKey(key)

            override fun itemContentType(contentType: ((item: T) -> Any?)?): (index: Int) -> Any? = data.itemContentType(contentType)
        }
    }

}

val <T : Any> PagingState<T>.isLoading: Boolean
    get() = this is PagingState.Loading

val <T : Any> PagingState<T>.isError: Boolean
    get() = this is PagingState.Error

val <T : Any> PagingState<T>.isEmpty: Boolean
    get() = this is PagingState.Empty

@OptIn(ExperimentalContracts::class)
fun <T : Any> PagingState<T>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is PagingState.Success<T>)
    }
    return this is PagingState.Success
}

val <T : Any> PagingState<T>.isRefreshing: Boolean
    get() =
        if (this is PagingState.Success) {
            isRefreshing
        } else {
            isLoading
        }

suspend fun <T : Any> PagingState<T>.refreshSuspend() {
    if (this is PagingState.Success) {
        refreshSuspend()
    }
}

inline fun <T : Any> PagingState<T>.onLoading(block: () -> Unit): PagingState<T> {
    if (this is PagingState.Loading) {
        block()
    }
    return this
}

inline fun <T : Any> PagingState<T>.onError(block: (Throwable) -> Unit): PagingState<T> {
    if (this is PagingState.Error) {
        block(error)
    }
    return this
}

inline fun <T : Any> PagingState<T>.onEmpty(block: PagingState.Empty<T>.() -> Unit): PagingState<T> {
    if (this is PagingState.Empty) {
        block(this)
    }
    return this
}

inline fun <T : Any> PagingState<T>.onSuccess(block: PagingState.Success<T>.() -> Unit): PagingState<T> {
    if (this is PagingState.Success) {
        block(this)
    }
    return this
}

inline fun LoadState.onLoading(block: () -> Unit): LoadState {
    if (this is LoadState.Loading) {
        block()
    }
    return this
}

inline fun LoadState.onError(block: (Throwable) -> Unit): LoadState {
    if (this is LoadState.Error) {
        block(error)
    }
    return this
}

inline fun LoadState.onEndOfList(block: () -> Unit): LoadState {
    if (this is LoadState.NotLoading && endOfPaginationReached) {
        block()
    }
    return this
}

@Composable
internal fun <T : Any> UiState<LazyPagingItems<T>>.toPagingState(): PagingState<T> =
    when (this) {
        is UiState.Loading -> PagingState.Loading()
        is UiState.Error -> PagingState.Error(throwable)
        is UiState.Success -> data.toPagingState()
    }

@Composable
internal fun <T : Any> LazyPagingItems<T>.toPagingState(): PagingState<T> {
    if (itemCount > 0) {
        return PagingState.Success.PagingSuccess(
            data = this,
            appendState = loadState.append,
        )
    } else if (loadState.refresh == LoadState.Loading ||
        loadState.prepend == LoadState.Loading ||
        loadState.append == LoadState.Loading
    ) {
        return PagingState.Loading()
    } else if (loadState.refresh is LoadState.Error ||
        loadState.prepend is LoadState.Error
    ) {
        return PagingState.Error(
            (loadState.refresh as? LoadState.Error)?.error
                ?: (loadState.prepend as LoadState.Error).error,
        )
    } else {
        return PagingState.Empty(this::refresh)
    }
}