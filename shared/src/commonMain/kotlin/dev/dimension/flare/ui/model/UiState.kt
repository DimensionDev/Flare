package dev.dimension.flare.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

@Immutable
public sealed class UiState<T : Any> {
    @Immutable
    public data class Success<T : Any>(
        val data: T,
    ) : UiState<T>()

    @Immutable
    public data class Error<T : Any>(
        val throwable: Throwable,
    ) : UiState<T>()

    @Immutable
    public class Loading<T : Any> : UiState<T>()
}

public inline fun <T : Any, R : Any> UiState<T>.map(transform: (T) -> R): UiState<R> =
    when (this) {
        is UiState.Success ->
            try {
                UiState.Success(transform(data))
            } catch (e: Throwable) {
                UiState.Error(e)
            }
        is UiState.Error -> UiState.Error(throwable)
        is UiState.Loading -> UiState.Loading()
    }

public inline fun <T : Any, R : Any> UiState<T>.mapNotNull(transform: (T) -> R?): UiState<R> =
    when (this) {
        is UiState.Success -> transform(data)?.let { UiState.Success(it) } ?: UiState.Error(IllegalStateException())
        is UiState.Error -> UiState.Error(throwable)
        is UiState.Loading -> UiState.Loading()
    }

public inline fun <T : Any, R : Any> UiState<T>.flatMap(
    onError: (Throwable) -> UiState<R> = { UiState.Error(it) },
    transform: (T) -> UiState<R>,
): UiState<R> =
    when (this) {
        is UiState.Success ->
            try {
                transform(data)
            } catch (e: Throwable) {
                onError(e)
            }
        is UiState.Error -> onError(throwable)
        is UiState.Loading -> UiState.Loading()
    }

public fun <T : Any> List<UiState<T>>.merge(requireAllSuccess: Boolean = true): UiState<List<T>> {
    val success = filterIsInstance<UiState.Success<T>>().map { it.data }
    val error = filterIsInstance<UiState.Error<T>>().map { it.throwable }
    val loading = filterIsInstance<UiState.Loading<T>>()

    return when {
        requireAllSuccess && success.size != size && loading.isEmpty() ->
            UiState.Error(IllegalStateException("Not all success"))
        error.isNotEmpty() -> UiState.Error(error.first())
        loading.isNotEmpty() -> UiState.Loading()
        else -> UiState.Success(success)
    }
}

public inline fun <T : Any> UiState<T>.onSuccess(action: (T) -> Unit): UiState<T> =
    apply {
        if (this is UiState.Success) {
            action(data)
        }
    }

public inline fun <T : Any> UiState<T>.onError(action: (Throwable) -> Unit): UiState<T> =
    apply {
        if (this is UiState.Error) {
            action(throwable)
        }
    }

public inline fun <T : Any> UiState<T>.onLoading(action: () -> Unit): UiState<T> =
    apply {
        if (this is UiState.Loading) {
            action()
        }
    }

public fun <T : Any> UiState<T>.takeSuccess(): T? = (this as? UiState.Success)?.data

public fun <T : Any> UiState<T>.takeSuccessOr(value: T): T = (this as? UiState.Success)?.data ?: value

public val <T : Any> UiState<T>.isSuccess: Boolean get() = this is UiState.Success
public val <T : Any> UiState<T>.isError: Boolean get() = this is UiState.Error
public val <T : Any> UiState<T>.isLoading: Boolean get() = this is UiState.Loading

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
