package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

/** Offset is the item's top edge relative to the viewport, in density independent points. */
@Immutable
public data class TimelineReadingPosition(
    val itemKey: String,
    val offset: Double,
)
