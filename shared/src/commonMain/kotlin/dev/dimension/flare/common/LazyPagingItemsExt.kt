package dev.dimension.flare.common

import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import app.cash.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull


suspend fun <T: Any> LazyPagingItems<T>.refreshSuspend() {
    refresh()
    snapshotFlow { loadState }
        .distinctUntilChanged()
        .filter {
            it.refresh is LoadState.NotLoading
        }
        .firstOrNull()
}
