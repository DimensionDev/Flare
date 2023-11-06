package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

actual abstract class PresenterBase<Model: Any> {

    private val scope = CoroutineScope(Dispatchers.IO)

    val models: StateFlow<Model> by lazy {
        scope.launchMolecule2(mode = RecompositionMode.Immediate) {
            body()
        }
    }
    private var job: Job? = null

    fun subscribe(listener: ModelListener<Model>) {
        job = scope.launch {
            models.collect {
                listener.onModelChanged(it)
            }
        }
    }

    @Composable
    internal actual abstract fun body(): Model

    fun unsubscribe() {
        job?.cancel()
        job = null
    }
}

fun interface ModelListener<Model> {
    fun onModelChanged(model: Model)
}