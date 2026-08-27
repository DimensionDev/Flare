package dev.dimension.flare.ui

import androidx.compose.runtime.Immutable

/** One-axis size requested from a renderer. Values use dp on Android and points on Apple platforms. */
@Immutable
public sealed interface FlareSize {
    public data object Wrap : FlareSize

    public data object Fill : FlareSize

    @Immutable
    public data class Fixed(
        public val value: Float,
    ) : FlareSize {
        init {
            require(value.isFinite() && value >= 0f) {
                "A fixed Flare size must be finite and non-negative."
            }
        }
    }
}

/** Immutable metadata applied to one primitive. */
@Immutable
public data class FlareModifier(
    public val testTag: String? = null,
    public val width: FlareSize = FlareSize.Wrap,
    public val height: FlareSize = FlareSize.Wrap,
) {
    init {
        require(testTag == null || testTag.isNotBlank()) {
            "A test tag cannot be blank."
        }
    }

    public companion object {
        public val None: FlareModifier = FlareModifier()
    }

    public fun fillMaxWidth(): FlareModifier = copy(width = FlareSize.Fill)

    public fun fillMaxHeight(): FlareModifier = copy(height = FlareSize.Fill)

    public fun fillMaxSize(): FlareModifier = copy(width = FlareSize.Fill, height = FlareSize.Fill)

    public fun width(value: Float): FlareModifier = copy(width = FlareSize.Fixed(value))

    public fun height(value: Float): FlareModifier = copy(height = FlareSize.Fixed(value))
}
