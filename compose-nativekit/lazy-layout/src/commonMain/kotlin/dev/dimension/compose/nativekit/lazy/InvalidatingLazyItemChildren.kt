package dev.dimension.compose.nativekit.lazy

import dev.dimension.compose.nativekit.NativeKitChildren

/** Invalidates native item geometry after one child-composition apply transaction completes. */
internal class InvalidatingLazyItemChildren(
    private val delegate: NativeKitChildren,
    private val onContentChanged: () -> Unit,
) : NativeKitChildren by delegate {
    override fun onEndChanges() {
        delegate.onEndChanges()
        onContentChanged()
    }
}
