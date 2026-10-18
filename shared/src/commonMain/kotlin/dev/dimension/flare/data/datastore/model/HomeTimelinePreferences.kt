package dev.dimension.flare.data.datastore.model

import kotlinx.serialization.Serializable

/** Device-local navigation state, excluded from exported settings. */
@Serializable
internal data class HomeTimelinePreferences(
    val selectedTabId: String? = null,
)
