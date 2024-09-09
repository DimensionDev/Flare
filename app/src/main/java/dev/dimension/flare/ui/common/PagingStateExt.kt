package dev.dimension.flare.ui.common

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess

fun <T : Any> LazyListScope.items(
    state: PagingState<T>,
    emptyContent: @Composable LazyItemScope.() -> Unit = {},
    errorContent: @Composable LazyItemScope.(Throwable) -> Unit = {},
    loadingContent: @Composable LazyItemScope.() -> Unit = {},
    loadingCount: Int = 10,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    state
        .onSuccess {
            items(itemCount) { index ->
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
