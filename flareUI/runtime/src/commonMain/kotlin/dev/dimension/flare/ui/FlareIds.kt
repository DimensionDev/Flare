package dev.dimension.flare.ui

import androidx.compose.runtime.Immutable

/** Compile-time identity for one renderer family. */
public interface FlareBackend

/**
 * Strongly typed identity for one generated primitive.
 *
 * Equality intentionally uses object identity. Every primitive owns one generated token which is
 * shared by its composable API and all renderer plugins in the same application binary.
 */
@Immutable
public class FlareComponentType<out W : FlareWidget>(
    public val debugName: String,
) {
    init {
        require(debugName.isNotBlank()) { "A Flare component debug name cannot be blank." }
    }

    override fun toString(): String = debugName
}

/** Stable identity for a named child group on a primitive. */
@Immutable
public data class FlareSlotId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "A Flare slot ID cannot be blank." }
    }

    override fun toString(): String = value
}
