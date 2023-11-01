package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable

expect abstract class PresenterBase<Model: Any>() {
    @Composable
    internal abstract fun body(): Model
}