package dev.dimension.flare.ui.lazy

import dev.dimension.flare.ui.FlareChildren

/** Invalidates native item geometry after one child-composition apply transaction completes. */
internal class InvalidatingLazyItemChildren(
    private val delegate: FlareChildren,
    private val onContentChanged: () -> Unit,
) : FlareChildren by delegate {
    override fun onEndChanges() {
        delegate.onEndChanges()
        onContentChanged()
    }
}
