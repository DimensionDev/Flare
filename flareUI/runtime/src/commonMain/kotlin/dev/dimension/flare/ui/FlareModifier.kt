package dev.dimension.flare.ui

/**
 * Ordered, immutable metadata applied to one primitive.
 */
public interface FlareModifier {
    public fun <R> foldIn(
        initial: R,
        operation: (R, Element) -> R,
    ): R

    public infix fun then(other: FlareModifier): FlareModifier =
        when {
            other === Companion -> this
            this === Companion -> other
            else -> CombinedFlareModifier(this, other)
        }

    public interface Element : FlareModifier {
        override fun <R> foldIn(
            initial: R,
            operation: (R, Element) -> R,
        ): R = operation(initial, this)
    }

    public companion object : FlareModifier {
        override fun <R> foldIn(
            initial: R,
            operation: (R, Element) -> R,
        ): R = initial

        override fun toString(): String = "FlareModifier"
    }
}

private data class CombinedFlareModifier(
    private val outer: FlareModifier,
    private val inner: FlareModifier,
) : FlareModifier {
    override fun <R> foldIn(
        initial: R,
        operation: (R, FlareModifier.Element) -> R,
    ): R = inner.foldIn(outer.foldIn(initial, operation), operation)
}

/** Returns the last element of [T], matching normal modifier override semantics. */
public inline fun <reified T : FlareModifier.Element> FlareModifier.lastElementOfType(): T? =
    foldIn<T?>(null) { result, element -> element as? T ?: result }
