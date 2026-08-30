package dev.dimension.flare.ui

import androidx.compose.runtime.Immutable

/** Immutable metadata applied to one primitive. */
@Immutable
public data class FlareModifier(
    public val testTag: String? = null,
) {
    init {
        require(testTag == null || testTag.isNotBlank()) {
            "A test tag cannot be blank."
        }
    }

    public companion object {
        public val None: FlareModifier = FlareModifier()
    }
}
