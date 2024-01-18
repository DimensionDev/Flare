package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

expect abstract class PresenterBase<Model : Any>() {
    val models: StateFlow<Model>

    @Composable
    internal abstract fun body(): Model
}
