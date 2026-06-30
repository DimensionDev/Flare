package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.humanizer.Formatter.humanize
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public data class UiNumber public constructor(
    public val value: Long,
) {
    public val humanized: String by lazy {
        value.takeIf { it > 0 }?.humanize().orEmpty()
    }
}
