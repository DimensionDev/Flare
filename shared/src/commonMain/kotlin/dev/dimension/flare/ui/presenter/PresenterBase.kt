package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.StateFlow

expect abstract class PresenterBase<Model : Any>() {
    val models: StateFlow<Model>

    @Composable
    internal abstract fun body(): Model
}

class CounterPresenter : PresenterBase<CounterPresenter.State>() {
    @Immutable
    interface State {
        val count: Int

        fun increment()

        fun decrement()

        fun reset()
    }

    @Composable
    override fun body(): State {
        var count by remember { mutableStateOf(0) }
        return object : State {
            override val count = count

            override fun increment() {
                count++
            }

            override fun decrement() {
                count--
            }

            override fun reset() {
                count = 0
            }
        }
    }
}
