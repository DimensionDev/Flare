package dev.dimension.flare.ui.screen.home

import dev.dimension.flare.AppContext
import dev.dimension.flare.ui.presenter.CounterPresenter
import org.gtkkn.bindings.gtk.Box
import org.gtkkn.bindings.gtk.Button
import org.gtkkn.bindings.gtk.Label
import org.gtkkn.bindings.gtk.Orientation
import org.gtkkn.bindings.gtk.Widget
import dev.dimension.flare.observe

internal fun AppContext.homeScreen(): Widget {
    val presenter = CounterPresenter()
    return Box(Orientation.VERTICAL, 0).apply {
        Button().apply {
            setLabel("Increment")
            connectClicked {
                presenter.models.value.increment()
            }
            append(this)
        }
        Button().apply {
            setLabel("Decrement")
            connectClicked {
                presenter.models.value.decrement()
            }
            append(this)
        }
        Button().apply {
            setLabel("Reset")
            connectClicked {
                presenter.models.value.reset()
            }
            append(this)
        }
        Label().apply {
            observe(presenter.models) {
                setText(it.count.toString())
            }
            append(this)
        }
    }
}
