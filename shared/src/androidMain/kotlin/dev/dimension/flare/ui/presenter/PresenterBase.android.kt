package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

actual abstract class PresenterBase<Model : Any> {
    // For android, just use invoke() to get the model
    actual val models: StateFlow<Model> by lazy<StateFlow<Model>> {
        throw NotImplementedError(
            "PresenterBase.models is not implemented for android, use invoke() instead",
        )
    }

    @Composable
    internal actual abstract fun body(): Model
}

@Composable
operator fun <Model : Any> PresenterBase<Model>.invoke(): Model = body()
