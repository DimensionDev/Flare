package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dev.dimension.flare.common.PlatformDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

public actual abstract class PresenterBase<Model : Any> {
    private val scope = CoroutineScope(PlatformDispatchers.IO)

    public actual val models: StateFlow<Model> by lazy {
        scope.launchMolecule(RecompositionMode.Immediate) {
            body()
        }
    }

    public fun close() {
        scope.cancel()
    }

    @Composable
    public actual abstract fun body(): Model
}
