package dev.dimension.flare.ui.model

import kotlinx.datetime.Instant

data class UiKeywordFilter(
    val keyword: String,
    val forTimeline: Boolean,
    val forNotification: Boolean,
    val forSearch: Boolean,
    val expiredAt: Instant?,
)
