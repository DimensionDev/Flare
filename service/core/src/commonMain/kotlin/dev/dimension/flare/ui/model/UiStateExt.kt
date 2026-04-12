package dev.dimension.flare.ui.model

import androidx.paging.LoadState
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.CacheableState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal fun <T : Any> CacheableState<T>.toUi(): UiState<T> =
    data?.let {
        UiState.Success(it)
    } ?: run {
        when (val state = refreshState) {
            is LoadState.Error -> UiState.Error(state.error)
            LoadState.Loading -> UiState.Loading()
            is LoadState.NotLoading -> UiState.Error(IllegalStateException("Data is null"))
        }
    }

internal fun <T : Any> CacheData<T>.toUi(): Flow<UiState<T>> =
    combine(data, refreshState) { data, refresh ->
        if (data is CacheState.Success) {
            UiState.Success(data.data)
        } else {
            when (refresh) {
                is LoadState.Error -> UiState.Error(refresh.error)
                LoadState.Loading -> UiState.Loading()
                is LoadState.NotLoading -> UiState.Error(IllegalStateException("Data is null"))
            }
        }
    }
