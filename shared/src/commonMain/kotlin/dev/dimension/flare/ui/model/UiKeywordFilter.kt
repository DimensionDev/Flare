package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.humanizer.humanize
import kotlinx.datetime.Instant

data class UiKeywordFilter(
    val keyword: String,
    val forTimeline: Boolean,
    val forNotification: Boolean,
    val forSearch: Boolean,
    val expiredAt: Instant?,
) {
    val humanizedExpiredAt: String?
        get() = expiredAt?.humanize()
}
