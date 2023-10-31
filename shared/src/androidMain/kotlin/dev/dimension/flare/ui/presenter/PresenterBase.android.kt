package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable

actual abstract class PresenterBase<Model> {

    @Composable
    operator fun invoke(): Model {
        return body()
    }

    @Composable
    internal actual abstract fun body(): Model
}