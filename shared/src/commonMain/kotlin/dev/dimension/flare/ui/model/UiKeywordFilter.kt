package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

@Immutable
public data class UiKeywordFilter(
    val keyword: String,
    val forTimeline: Boolean,
    val forNotification: Boolean,
    val forSearch: Boolean,
    val expiredAt: Instant?,
)
