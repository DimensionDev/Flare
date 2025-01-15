package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.render.UiDateTime

public data class UiRssSource(
    val url: String,
    val title: String?,
    val lastUpdate: UiDateTime,
)
