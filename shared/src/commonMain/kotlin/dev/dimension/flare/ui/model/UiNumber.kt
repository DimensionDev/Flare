package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.humanizer.Formatter.humanize

@Immutable
public data class UiNumber internal constructor(
    public val value: Long,
) {
    public val humanized: String = value.takeIf { it > 0 }?.humanize().orEmpty()
}
