package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dimension.flare.rust.PresenterException
import dev.dimension.flare.rust.UiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Maps generated UniFFI types without changing generated code or forwarding business actions. */
internal class RustPresenterBinding<P : AutoCloseable, S : Any, O : AutoCloseable>(
    val key: String,
    val snapshot: (P) -> S,
    val subscribe: (P) -> O,
    val nextState: suspend (O) -> S?,
    val stop: (O) -> Unit,
    val shutdown: (P) -> Unit,
)

/** Owned by the navigation entry, so configuration changes retain the native session. */
internal class RustPresenter<P : AutoCloseable, S : Any, O : AutoCloseable>(
    private val binding: RustPresenterBinding<P, S, O>,
    factory: () -> P,
) : ViewModel(),
    AutoCloseable {
    private var native: P? = null
    private var subscription: O? = null
    private val mutableState = MutableStateFlow<S?>(null)
    private val mutableError = MutableStateFlow<UiError?>(null)
    val state = mutableState.asStateFlow()
    val error = mutableError.asStateFlow()
    private var closed = false

    init {
        try {
            val presenter = factory().also { native = it }
            val observer = binding.subscribe(presenter).also { subscription = it }
            mutableState.value = binding.snapshot(presenter)
            viewModelScope.launch {
                reportErrors {
                    while (!closed) {
                        mutableState.value = binding.nextState(observer) ?: break
                    }
                }
            }
        } catch (error: Exception) {
            close()
            if (error is CancellationException) throw error
            mutableError.value = error.toUiError()
        }
    }

    fun perform(action: suspend P.() -> Unit) {
        if (closed) return
        val presenter = native ?: return
        viewModelScope.launch {
            mutableError.value = null
            reportErrors { presenter.action() }
        }
    }

    private suspend fun reportErrors(action: suspend () -> Unit) {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!closed) mutableError.value = error.toUiError()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        subscription?.let(binding.stop)
        native?.let(binding.shutdown)
        viewModelScope.cancel()
        subscription?.close()
        native?.close()
        subscription = null
        native = null
    }

    override fun onCleared() {
        close()
    }
}

private fun Exception.toUiError(): UiError =
    when (this) {
        is PresenterException.Failed -> error
        is PresenterException.Closed -> UiError("presenter_closed", "Presenter is closed")
        is PresenterException.RuntimeUnavailable -> UiError("runtime_unavailable", "Rust runtime could not start")
        else -> UiError("native_error", message?.takeIf { it.isNotBlank() } ?: toString())
    }

/** Use a distinct key for multiple instances of the same presenter in one navigation entry. */
@Composable
internal fun <P : AutoCloseable, S : Any, O : AutoCloseable> rememberRustPresenter(
    binding: RustPresenterBinding<P, S, O>,
    key: String = binding.key,
    factory: () -> P,
): RustPresenter<P, S, O> = viewModel(key = key) { RustPresenter(binding, factory) }
