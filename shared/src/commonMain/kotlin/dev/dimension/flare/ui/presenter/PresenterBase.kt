package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable

expect abstract class PresenterBase<Model>() {
    @Composable
    protected abstract fun body(): Model
}