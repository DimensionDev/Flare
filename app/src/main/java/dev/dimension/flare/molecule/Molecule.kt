package dev.dimension.flare.molecule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cash.molecule.AndroidUiDispatcher
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope

private class PresenterHolder<T>(
    body: @Composable () -> T,
) : ViewModel() {
    private val scope = CoroutineScope(viewModelScope.coroutineContext + AndroidUiDispatcher.Main)
    val state = scope.launchMolecule(mode = RecompositionMode.ContextClock, body = body)
}

/**
 * Return State, use it in your Compose UI
 * The molecule scope will be managed by the [ViewModel], so it has the same lifecycle as the [ViewModel]
 * @param body The body of the molecule presenter
 * @return State
 */
@Composable
fun <T> producePresenter(
    key: String? = null,
    body: @Composable () -> T,
): State<T> = createPresenter(body, key)

@Composable
private fun <T> createPresenter(
    body: @Composable () -> T,
    key: String? = null,
    holder: PresenterHolder<T> =
        viewModel(key = key + currentCompositeKeyHash.toString()) {
            PresenterHolder<T>(body)
        },
): State<T> = holder.state.collectAsStateWithLifecycle()
