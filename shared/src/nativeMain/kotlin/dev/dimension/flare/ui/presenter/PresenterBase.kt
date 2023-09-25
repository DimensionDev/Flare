package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

actual abstract class PresenterBase<Model> {

    private val scope = CoroutineScope(app.cash.molecule.DisplayLinkClock)

    val models: StateFlow<Model> by lazy(LazyThreadSafetyMode.NONE) {
        scope.launchMolecule(mode = RecompositionMode.ContextClock) {
            body()
        }
    }

    @Composable
    protected actual abstract fun body(): Model
}