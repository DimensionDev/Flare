package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

public expect abstract class PresenterBase<Model : Any>() {

    public val models: StateFlow<Model>

    @Composable
    public abstract fun body(): Model
}
