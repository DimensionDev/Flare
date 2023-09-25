package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable

actual abstract class PresenterBase<Model> {

    @Composable
    operator fun invoke(): Model {
        return body()
    }

    @Composable
    protected actual abstract fun body(): Model
}