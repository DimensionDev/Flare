package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

public actual abstract class PresenterBase<Model : Any> {
    // For jvm, just use invoke() to get the model
    public actual val models: StateFlow<Model> by lazy<StateFlow<Model>> {
        throw NotImplementedError(
            "PresenterBase.models is not implemented for jvm, use invoke() instead",
        )
    }

    @Composable
    internal actual abstract fun body(): Model
}

@Composable
public operator fun <Model : Any> PresenterBase<Model>.invoke(): Model = body()
