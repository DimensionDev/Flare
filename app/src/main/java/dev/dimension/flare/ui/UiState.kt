package dev.dimension.flare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import dev.dimension.flare.common.CacheableState
import dev.dimension.flare.common.LoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal sealed interface UiState<T> {
    data class Success<T>(val data: T) : UiState<T>
    data class Error<T>(val throwable: Throwable) : UiState<T>
    class Loading<T> : UiState<T>
}

internal fun <T> Flow<T>.toUiState(): Flow<UiState<T>> = map<T, UiState<T>> { UiState.Success(it) }
    .onStart { emit(UiState.Loading()) }
    .catch { emit(UiState.Error(it)) }

@Composable
internal fun <T> Flow<T>.collectAsUiState(initial: UiState<T> = UiState.Loading()): State<UiState<T>> =
    remember(this) { toUiState() }.collectAsState(initial)

internal inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Success -> UiState.Success(transform(data))
    is UiState.Error -> UiState.Error(throwable)
    is UiState.Loading -> UiState.Loading()
}

internal inline fun <T, R> UiState<T>.flatMap(transform: (T) -> UiState<R>): UiState<R> = when (this) {
    is UiState.Success -> transform(data)
    is UiState.Error -> UiState.Error(throwable)
    is UiState.Loading -> UiState.Loading()
}

internal inline fun <T> UiState<T>.onSuccess(action: (T) -> Unit): UiState<T> = apply {
    if (this is UiState.Success) {
        action(data)
    }
}

internal inline fun <T> UiState<T>.onError(action: (Throwable) -> Unit): UiState<T> = apply {
    if (this is UiState.Error) {
        action(throwable)
    }
}

internal inline fun <T> UiState<T>.onLoading(action: () -> Unit): UiState<T> = apply {
    if (this is UiState.Loading) {
        action()
    }
}

internal fun <T> CacheableState<T>.toUi(): UiState<T> {
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
