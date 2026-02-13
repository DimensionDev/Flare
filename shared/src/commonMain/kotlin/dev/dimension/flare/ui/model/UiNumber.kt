package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.humanizer.Formatter.humanize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Immutable
public data class UiNumber internal constructor(
    public val value: Long,
) {
    @Transient
    public val humanized: String = value.takeIf { it > 0 }?.humanize().orEmpty()
}
