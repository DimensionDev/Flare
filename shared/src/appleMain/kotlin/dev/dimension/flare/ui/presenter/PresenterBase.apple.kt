package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.DisplayLinkClock
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCRefinement

public actual abstract class PresenterBase<Model : Any> : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.Main + DisplayLinkClock)

    public actual val models: StateFlow<Model> by lazy {
        scope.launchMolecule(RecompositionMode.ContextClock) {
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

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public operator fun <Model : Any> PresenterBase<Model>.invoke(): Model = body()
