package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.humanizer.Formatter.humanize

public data class UiNumber internal constructor(
    public val value: Long,
) {
    public val humanized: String = value.takeIf { it > 0 }?.humanize().orEmpty()
}
