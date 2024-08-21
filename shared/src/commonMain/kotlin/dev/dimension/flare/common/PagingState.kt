package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.dimension.flare.ui.model.UiState
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
        private val data: LazyPagingItems<T>,
    ) : PagingState<T>() {
        fun refresh() {
            data.refresh()
        }
    }

    @Immutable
    data class Success<T : Any> internal constructor(
        private val data: LazyPagingItems<T>,
        val appendState: AppendState,
    ) : PagingState<T>() {
        val itemCount: Int
            get() = data.itemCount
        val isRefreshing: Boolean
            get() = data.isRefreshing

        operator fun get(index: Int): T? = data[index]

        fun peek(index: Int): T? = data.peek(index)

        suspend fun refreshSuspend() {
            data.refreshSuspend()
        }

        fun retry() {
            data.retry()
        }

        fun itemKey(key: ((item: T) -> Any)? = null): (index: Int) -> Any = data.itemKey(key)

        fun itemContentType(contentType: ((item: T) -> Any?)? = null): (index: Int) -> Any? = data.itemContentType(contentType)

        @Immutable
        sealed interface AppendState {
            @Immutable
            data object Loading : AppendState

            @Immutable
            data class Error(
                val error: Throwable,
            ) : AppendState

            @Immutable
            data object EndOfList : AppendState
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

inline fun PagingState.Success.AppendState.onLoading(block: () -> Unit): PagingState.Success.AppendState {
    if (this is PagingState.Success.AppendState.Loading) {
        block()
    }
    return this
}

inline fun PagingState.Success.AppendState.onError(block: (Throwable) -> Unit): PagingState.Success.AppendState {
    if (this is PagingState.Success.AppendState.Error) {
        block(error)
    }
    return this
}

inline fun PagingState.Success.AppendState.onEndOfList(block: () -> Unit): PagingState.Success.AppendState {
    if (this is PagingState.Success.AppendState.EndOfList) {
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
        return PagingState.Success(
            data = this,
            appendState =
                when (loadState.append) {
                    is LoadState.Loading -> PagingState.Success.AppendState.Loading
                    is LoadState.Error -> PagingState.Success.AppendState.Error((loadState.append as LoadState.Error).error)
                    is LoadState.NotLoading -> PagingState.Success.AppendState.EndOfList
                },
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
        return PagingState.Empty(this)
    }
}
