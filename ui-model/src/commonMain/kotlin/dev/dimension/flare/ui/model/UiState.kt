package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
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

@HiddenFromObjC
public inline fun <T : Any, R : Any> UiState<T>.map(transform: (T) -> R): UiState<R> =
    when (this) {
        is UiState.Success -> {
            try {
                UiState.Success(transform(data))
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }

        is UiState.Error -> {
            UiState.Error(throwable)
        }

        is UiState.Loading -> {
            UiState.Loading()
        }
    }

@HiddenFromObjC
public inline fun <T : Any, R : Any> UiState<T>.mapNotNull(transform: (T) -> R?): UiState<R> =
    when (this) {
        is UiState.Success -> transform(data)?.let { UiState.Success(it) } ?: UiState.Error(IllegalStateException())
        is UiState.Error -> UiState.Error(throwable)
        is UiState.Loading -> UiState.Loading()
    }

@HiddenFromObjC
public inline fun <T : Any, R : Any> UiState<T>.flatMap(
    onError: (Throwable) -> UiState<R> = { UiState.Error(it) },
    transform: (T) -> UiState<R>,
): UiState<R> =
    when (this) {
        is UiState.Success -> {
            try {
                transform(data)
            } catch (e: Exception) {
                onError(e)
            }
        }

        is UiState.Error -> {
            onError(throwable)
        }

        is UiState.Loading -> {
            UiState.Loading()
        }
    }

@HiddenFromObjC
public inline fun <T1 : Any, T2 : Any, R : Any> zipState(
    a: UiState<T1>,
    b: UiState<T2>,
    onError: (Throwable) -> UiState<R> = { UiState.Error(it) },
    transform: (T1, T2) -> R,
): UiState<R> =
    when {
        a is UiState.Loading || b is UiState.Loading -> {
            UiState.Loading()
        }

        a is UiState.Error -> {
            onError(a.throwable)
        }

        b is UiState.Error -> {
            onError(b.throwable)
        }

        a is UiState.Success && b is UiState.Success -> {
            try {
                UiState.Success(transform(a.data, b.data))
            } catch (e: Exception) {
                UiState.Error(e)
            }
        }

        else -> {
            UiState.Error(IllegalStateException("Unreachable"))
        }
    }

@HiddenFromObjC
public fun <T : Any> List<UiState<T>>.merge(requireAllSuccess: Boolean = true): UiState<List<T>> {
    val success = filterIsInstance<UiState.Success<T>>().map { it.data }
    val error = filterIsInstance<UiState.Error<T>>().map { it.throwable }
    val loading = filterIsInstance<UiState.Loading<T>>()

    return when {
        requireAllSuccess && success.size != size && loading.isEmpty() -> {
            UiState.Error(IllegalStateException("Not all success"))
        }

        error.isNotEmpty() -> {
            UiState.Error(error.first())
        }

        loading.isNotEmpty() -> {
            UiState.Loading()
        }

        else -> {
            UiState.Success(success)
        }
    }
}

@HiddenFromObjC
public inline fun <T : Any> UiState<T>.onSuccess(action: (T) -> Unit): UiState<T> =
    apply {
        if (this is UiState.Success) {
            action(data)
        }
    }

@HiddenFromObjC
public inline fun <T : Any> UiState<T>.onError(action: (Throwable) -> Unit): UiState<T> =
    apply {
        if (this is UiState.Error) {
            action(throwable)
        }
    }

@HiddenFromObjC
public inline fun <T : Any> UiState<T>.onLoading(action: () -> Unit): UiState<T> =
    apply {
        if (this is UiState.Loading) {
            action()
        }
    }

@HiddenFromObjC
public fun <T : Any> UiState<T>.takeSuccess(): T? = (this as? UiState.Success)?.data

@HiddenFromObjC
public fun <T : Any> UiState<T>.takeSuccessOr(value: T): T = (this as? UiState.Success)?.data ?: value

@HiddenFromObjC
public val <T : Any> UiState<T>.isSuccess: Boolean get() = this is UiState.Success

@HiddenFromObjC
public val <T : Any> UiState<T>.isError: Boolean get() = this is UiState.Error

@HiddenFromObjC
public val <T : Any> UiState<T>.isLoading: Boolean get() = this is UiState.Loading
