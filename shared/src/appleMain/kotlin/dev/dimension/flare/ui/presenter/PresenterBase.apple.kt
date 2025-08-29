package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCRefinement

public actual abstract class PresenterBase<Model : Any> : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.IO)

    public actual val models: StateFlow<Model> by lazy {
        scope.launchMolecule(mode = RecompositionMode.Immediate) {
            body()
        }
    }

    override fun close() {
        scope.cancel()
    }

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Composable
    public actual abstract fun body(): Model
}
