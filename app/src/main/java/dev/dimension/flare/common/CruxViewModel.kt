package dev.dimension.flare.common

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

@MainThread
internal class CruxViewModel<VM : Any>(
    bridge: AutoCloseable,
    createViewModel: (CoroutineScope) -> VM,
) : ViewModel(bridge) {
    val viewModel: VM =
        try {
            createViewModel(viewModelScope)
        } catch (failure: Throwable) {
            viewModelScope.cancel()
            runCatching { bridge.close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
}

/** An explicit key keeps different features separate despite generic erasure. */
@Composable
internal fun <Bridge : AutoCloseable, VM : Any> rememberCruxViewModel(
    key: String,
    createBridge: () -> Bridge,
    createViewModel: (Bridge, CoroutineScope) -> VM,
): VM =
    viewModel<CruxViewModel<VM>>(key = key) {
        val bridge = createBridge()
        CruxViewModel(bridge) { scope -> createViewModel(bridge, scope) }
    }.viewModel
