package dev.dimension.flare.common

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
public sealed class PagingState<T> {
    @Immutable
    public class Loading<T> internal constructor() : PagingState<T>()

    @Immutable
    public data class Error<T> internal constructor(
        val error: Throwable,
    ) : PagingState<T>()

    @Immutable
    public data class Empty<T : Any> internal constructor(
        private val onRefresh: () -> Unit,
    ) : PagingState<T>() {
        public fun refresh() {
            onRefresh()
        }
    }

    @Immutable
    public sealed class Success<T : Any> : PagingState<T>() {
        public abstract val itemCount: Int
        public abstract val isRefreshing: Boolean
        public abstract val appendState: LoadState

        public abstract operator fun get(index: Int): T?

        public abstract fun peek(index: Int): T?

        public abstract suspend fun refreshSuspend()

        public abstract fun retry()

        public abstract fun itemKey(key: ((item: T) -> Any)? = null): (index: Int) -> Any

        public abstract fun itemContentType(contentType: ((item: T) -> Any?)? = null): (index: Int) -> Any?

        @Immutable
        internal data class SingleSuccess<T : Any>(
            private val data: CacheableState<ImmutableList<T>>,
        ) : Success<T>() {
            override val itemCount: Int
                get() = data.data?.size ?: 0
            override val isRefreshing: Boolean
                get() = data.refreshState is LoadState.Loading

            override fun get(index: Int): T? = data.data?.getOrNull(index)

            override fun peek(index: Int): T? = data.data?.getOrNull(index)

            override suspend fun refreshSuspend() {
                data.refresh()
            }

            override fun retry() {
                data.refresh()
            }

            override val appendState: LoadState
                get() = LoadState.NotLoading(true)

            override fun itemContentType(contentType: ((item: T) -> Any?)?): (index: Int) -> Any? = { null }

            override fun itemKey(key: ((item: T) -> Any)?): (index: Int) -> Any = { it }
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

public val <T : Any> PagingState<T>.isLoading: Boolean
    get() = this is PagingState.Loading

public val <T : Any> PagingState<T>.isError: Boolean
    get() = this is PagingState.Error

public val <T : Any> PagingState<T>.isEmpty: Boolean
    get() = this is PagingState.Empty

@OptIn(ExperimentalContracts::class)
public fun <T : Any> PagingState<T>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is PagingState.Success<T>)
    }
    return this is PagingState.Success
}

public val <T : Any> PagingState<T>.isRefreshing: Boolean
    get() =
        if (this is PagingState.Success) {
            isRefreshing
        } else {
            isLoading
        }

public suspend fun <T : Any> PagingState<T>.refreshSuspend() {
    if (this is PagingState.Success) {
        refreshSuspend()
    }
}

public inline fun <T : Any> PagingState<T>.onLoading(block: () -> Unit): PagingState<T> {
    if (this is PagingState.Loading) {
        block()
    }
    return this
}

public inline fun <T : Any> PagingState<T>.onError(block: (Throwable) -> Unit): PagingState<T> {
    if (this is PagingState.Error) {
        block(error)
    }
    return this
}

public inline fun <T : Any> PagingState<T>.onEmpty(block: PagingState.Empty<T>.() -> Unit): PagingState<T> {
    if (this is PagingState.Empty) {
        block(this)
    }
    return this
}

public inline fun <T : Any> PagingState<T>.onSuccess(block: PagingState.Success<T>.() -> Unit): PagingState<T> {
    if (this is PagingState.Success) {
        block(this)
    }
    return this
}

public inline fun LoadState.onLoading(block: () -> Unit): LoadState {
    if (this is LoadState.Loading) {
        block()
    }
    return this
}

public inline fun LoadState.onError(block: (Throwable) -> Unit): LoadState {
    if (this is LoadState.Error) {
        block(error)
    }
    return this
}

public inline fun LoadState.onEndOfList(block: () -> Unit): LoadState {
    if (this is LoadState.NotLoading && endOfPaginationReached) {
        block()
    }
    return this
}

internal fun <T : Any> UiState<LazyPagingItems<T>>.toPagingState(): PagingState<T> =
    when (this) {
        is UiState.Loading -> PagingState.Loading()
        is UiState.Error -> PagingState.Error(throwable)
        is UiState.Success -> data.toPagingState()
    }

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

internal fun <T : Any> UiState<PagingState<T>>.flatten(): PagingState<T> =
    when (this) {
        is UiState.Loading -> PagingState.Loading()
        is UiState.Error -> PagingState.Error(throwable)
        is UiState.Success -> data
    }
