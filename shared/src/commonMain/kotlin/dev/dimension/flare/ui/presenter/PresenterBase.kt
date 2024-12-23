package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

public expect abstract class PresenterBase<Model : Any>() {
    public val models: StateFlow<Model>

    @Composable
    internal abstract fun body(): Model
}
