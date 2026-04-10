package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.model.UiIcon

@Immutable
public data class PlatformTypeMetadata(
    val displayName: String,
    val icon: UiIcon,
)
