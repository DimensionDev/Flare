package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

actual abstract class PresenterBase<Model : Any> {
    private val scope = CoroutineScope(Dispatchers.IO)

    actual val models: StateFlow<Model> by lazy {
        scope.launchMolecule(mode = RecompositionMode.Immediate) {
            body()
        }
    }

    @Composable
    internal actual abstract fun body(): Model
}

fun <Model : Any> PresenterBase<Model>.subscribe(listener: ModelListener<Model>) {
    CoroutineScope(Dispatchers.IO).launch {
        models.collect {
            listener.onModelChanged(it)
        }
    }
}

fun interface ModelListener<Model> {
    fun onModelChanged(model: Model)
}
