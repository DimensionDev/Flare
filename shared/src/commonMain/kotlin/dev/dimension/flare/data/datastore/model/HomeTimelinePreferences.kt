package dev.dimension.flare.data.datastore.model

import dev.dimension.flare.ui.model.TimelineReadingPosition
import kotlinx.serialization.Serializable

/** Device-local navigation state, excluded from exported settings. */
@Serializable
internal data class HomeTimelinePreferences(
    val selectedTabId: String? = null,
    val positions: Map<String, HomeTimelinePosition> = emptyMap(),
)

@Serializable
internal data class HomeTimelinePosition(
    val sourceKey: String,
    val position: TimelineReadingPosition,
    val updatedAt: Long,
)
