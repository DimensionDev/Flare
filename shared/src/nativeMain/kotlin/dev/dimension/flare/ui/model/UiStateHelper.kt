package dev.dimension.flare.ui.model

import app.cash.paging.compose.LazyPagingItems

object UiStateHelper {
    fun <T: Any> extraLazyPagingItem(state: UiState<LazyPagingItems<T>>): LazyPagingItems<T>? {
        return when (state) {
            is UiState.Error -> null
            is UiState.Loading -> null
            is UiState.Success -> state.data
        }
    }
}