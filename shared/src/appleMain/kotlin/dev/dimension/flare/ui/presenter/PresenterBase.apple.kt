package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.DisplayLinkClock
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import co.touchlab.skie.configuration.annotations.FlowInterop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCRefinement

actual abstract class PresenterBase<Model : Any> {
    private val scope = CoroutineScope(Dispatchers.Main + DisplayLinkClock)

    @FlowInterop.Enabled
    actual val models: StateFlow<Model> by lazy {
        scope.launchMolecule(mode = RecompositionMode.ContextClock) {
            body()
        }
    }

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Composable
    internal actual abstract fun body(): Model
}
