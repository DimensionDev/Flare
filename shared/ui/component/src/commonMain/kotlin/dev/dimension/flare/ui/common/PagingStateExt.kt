package dev.dimension.flare.ui.common

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.runtime.Composable
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess

public fun <T : Any> LazyListScope.items(
    state: PagingState<T>,
    emptyContent: @Composable LazyItemScope.() -> Unit = {},
    errorContent: @Composable LazyItemScope.(Throwable) -> Unit = {},
    loadingContent: @Composable LazyItemScope.() -> Unit = {},
    loadingCount: Int = 10,
    key: (PagingState.Success<T>.(index: Int) -> Any)? = null,
    contentType: PagingState.Success<T>.(index: Int) -> Any? = { null },
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    state
        .onSuccess {
            items(
                count = itemCount,
                key =
                    key?.let {
                        {
                            it(this, it)
                        }
                    },
                contentType = {
                    contentType(this, it)
                },
            ) { index ->
                val item = get(index)
                if (item != null) {
                    itemContent(item)
                } else {
                    loadingContent()
                }
            }
        }.onLoading {
            items(loadingCount) {
                loadingContent()
            }
        }.onError {
            item {
                errorContent(it)
            }
        }.onEmpty {
            item(content = emptyContent)
        }
}

public fun <T : Any> LazyStaggeredGridScope.items(
    state: PagingState<T>,
    emptyContent: @Composable LazyStaggeredGridItemScope.() -> Unit = {},
    errorContent: @Composable LazyStaggeredGridItemScope.(Throwable) -> Unit = {},
    loadingContent: @Composable LazyStaggeredGridItemScope.() -> Unit = {},
    loadingCount: Int = 10,
    key: (PagingState.Success<T>.(index: Int) -> Any)? = null,
    contentType: PagingState.Success<T>.(index: Int) -> Any? = { null },
    itemContent: @Composable LazyStaggeredGridItemScope.(T) -> Unit,
) {
    state
        .onSuccess {
            items(
                count = itemCount,
                key =
                    key?.let {
                        {
                            it(this, it)
                        }
                    },
                contentType = {
                    contentType(this, it)
                },
            ) { index ->
                val item = get(index)
                if (item != null) {
                    itemContent(item)
                } else {
                    loadingContent()
                }
            }
        }.onLoading {
            items(loadingCount) {
                loadingContent()
            }
        }.onError {
            item {
                errorContent(it)
            }
        }.onEmpty {
            item(content = emptyContent)
        }
}
