package dev.dimension.compose.nativekit

import androidx.compose.runtime.Immutable

/** One-axis size requested from a renderer. Values use dp on Android and points on Apple platforms. */
@Immutable
public sealed interface NativeKitSize {
    public data object Wrap : NativeKitSize

    public data object Fill : NativeKitSize

    @Immutable
    public data class Fixed(
        public val value: Float,
    ) : NativeKitSize {
        init {
            require(value.isFinite() && value >= 0f) {
                "A fixed NativeKit size must be finite and non-negative."
            }
        }
    }
}

/** Immutable metadata applied to one primitive. */
@Immutable
public data class NativeKitModifier(
    public val testTag: String? = null,
    public val width: NativeKitSize = NativeKitSize.Wrap,
    public val height: NativeKitSize = NativeKitSize.Wrap,
) {
    init {
        require(testTag == null || testTag.isNotBlank()) {
            "A test tag cannot be blank."
        }
    }

    public companion object {
        public val None: NativeKitModifier = NativeKitModifier()
    }

    public fun fillMaxWidth(): NativeKitModifier = copy(width = NativeKitSize.Fill)

    public fun fillMaxHeight(): NativeKitModifier = copy(height = NativeKitSize.Fill)

    public fun fillMaxSize(): NativeKitModifier = copy(width = NativeKitSize.Fill, height = NativeKitSize.Fill)

    public fun width(value: Float): NativeKitModifier = copy(width = NativeKitSize.Fixed(value))

    public fun height(value: Float): NativeKitModifier = copy(height = NativeKitSize.Fixed(value))
}
