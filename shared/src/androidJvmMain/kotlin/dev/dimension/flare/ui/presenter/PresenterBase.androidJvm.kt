package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public actual abstract class PresenterBase<Model : Any> {
    // For Android/JVM, just use invoke() to get the model
    public actual val models: StateFlow<Model> by lazy<StateFlow<Model>> {
        throw NotImplementedError(
            "PresenterBase.models is not implemented for android, use invoke() instead",
        )
    }

    public val moleculeFlow: Flow<Model> by lazy {
        moleculeFlow(RecompositionMode.Immediate) {
            body()
        }
    }

    @Composable
    public actual abstract fun body(): Model
}

@Composable
public operator fun <Model : Any> PresenterBase<Model>.invoke(): Model = body()
