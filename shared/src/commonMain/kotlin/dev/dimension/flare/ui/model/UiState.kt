package dev.dimension.flare.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.CacheableState
import dev.dimension.flare.common.LoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed class UiState<T : Any> {
    data class Success<T : Any>(val data: T) : UiState<T>()

    data class Error<T : Any>(val throwable: Throwable) : UiState<T>()

    class Loading<T : Any> : UiState<T>()
}

fun <T : Any> Flow<T>.toUiState(): Flow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
        .onStart { emit(UiState.Loading()) }
        .catch { emit(UiState.Error(it)) }

inline fun <T : Any, R : Any> UiState<T>.map(transform: (T) -> R): UiState<R> =
    when (this) {
        is UiState.Success -> UiState.Success(transform(data))
        is UiState.Error -> UiState.Error(throwable)
        is UiState.Loading -> UiState.Loading()
    }

inline fun <T : Any, R : Any> UiState<T>.flatMap(transform: (T) -> UiState<R>): UiState<R> =
    when (this) {
        is UiState.Success -> transform(data)
        is UiState.Error -> UiState.Error(throwable)
        is UiState.Loading -> UiState.Loading()
    }

inline fun <T : Any> UiState<T>.onSuccess(action: (T) -> Unit): UiState<T> =
    apply {
        if (this is UiState.Success) {
            action(data)
        }
    }

inline fun <T : Any> UiState<T>.onError(action: (Throwable) -> Unit): UiState<T> =
    apply {
        if (this is UiState.Error) {
            action(throwable)
        }
    }

inline fun <T : Any> UiState<T>.onLoading(action: () -> Unit): UiState<T> =
    apply {
        if (this is UiState.Loading) {
            action()
        }
    }

@Composable
internal fun <T : Any> Flow<T>.collectAsUiState(initial: UiState<T> = UiState.Loading()): State<UiState<T>> =
    remember(this) { toUiState() }.collectAsState(initial)

fun <T : Any> CacheableState<T>.toUi(): UiState<T> {
    return data?.let {
        UiState.Success(it)
    } ?: run {
        when (val state = refreshState) {
            is LoadState.Error -> UiState.Error(state.error)
            LoadState.Loading -> UiState.Loading()
            LoadState.Success -> UiState.Error(IllegalStateException("Data is null"))
        }
    }
}

internal fun <T : Any> CacheData<T>.toUi(): Flow<UiState<T>> {
    return combine(data, refreshState) { data, refresh ->
        if (data is CacheState.Success) {
            UiState.Success(data.data)
        } else {
            when (refresh) {
                is LoadState.Error -> UiState.Error(refresh.error)
                LoadState.Loading -> UiState.Loading()
                LoadState.Success -> UiState.Error(IllegalStateException("Data is null"))
            }
        }
    }
}
