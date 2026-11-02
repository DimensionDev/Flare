package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Offset is the item's top edge relative to the viewport, in density independent points. */
@Serializable
@Immutable
public data class TimelineReadingPosition(
    val index: Int,
    val offset: Double,
)
