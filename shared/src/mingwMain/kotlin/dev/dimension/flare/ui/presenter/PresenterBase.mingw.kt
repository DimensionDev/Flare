package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlin.experimental.ExperimentalObjCRefinement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

actual abstract class PresenterBase<Model> {

    private val scope = CoroutineScope(Dispatchers.Main)

    val models: StateFlow<Model> by lazy {
        scope.launchMolecule(mode = RecompositionMode.Immediate) {
            body()
        }
    }

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Composable
    internal actual abstract fun body(): Model
}