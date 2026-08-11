package dev.dimension.flare.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.paging.LoadState
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.CacheableState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@Composable
@HiddenFromObjC
public fun <T : Any> Flow<T>.collectAsUiState(initial: UiState<T> = UiState.Loading()): State<UiState<T>> =
    produceState(initial, this) {
        onStart {
            value = UiState.Loading()
        }.catch {
            value = UiState.Error(it)
        }.collect {
            value = UiState.Success(it)
        }
    }

@OptIn(ExperimentalObjCRefinement::class)
@Composable
@HiddenFromObjC
public fun <T : Any> Flow<UiState<T>>.flattenUiState(initial: UiState<T> = UiState.Loading()): State<UiState<T>> =
    produceState(initial, this) {
        onStart {
            value = UiState.Loading()
        }.catch {
            value = UiState.Error(it)
        }.collect {
            value = it
        }
    }

public fun <T : Any> CacheableState<T>.toUi(): UiState<T> =
    data?.let {
        UiState.Success(it)
    } ?: run {
        when (val state = refreshState) {
            is LoadState.Error -> UiState.Error(state.error)
            LoadState.Loading -> UiState.Loading()
            is LoadState.NotLoading -> UiState.Error(IllegalStateException("Data is null"))
        }
    }

public fun <T : Any> CacheData<T>.toUi(): Flow<UiState<T>> =
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
