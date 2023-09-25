package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.DisplayLinkClock
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCRefinement

actual abstract class PresenterBase<Model> {

    private val scope = CoroutineScope(DisplayLinkClock)

    val models: StateFlow<Model> = scope.launchMolecule(mode = RecompositionMode.Immediate) {
        body()
    }

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Composable
    protected actual abstract fun body(): Model
}