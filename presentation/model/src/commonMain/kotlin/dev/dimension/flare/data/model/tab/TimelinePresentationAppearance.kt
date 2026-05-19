package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.toPatch

public val TimelinePresentation.appearance: AppearancePatch?
    get() = appearanceOverride?.toPatch()

public fun TimelinePresentation.withOverrides(
    titleOverride: String?,
    iconOverride: IconType?,
    appearancePatch: AppearancePatch?,
    enabled: Boolean,
    filterConfig: TimelineFilterConfig,
): TimelinePresentation =
    TimelinePresentation(
        titleOverride = titleOverride,
        iconOverride = iconOverride,
        appearanceOverride = appearancePatch?.takeUnless { it == AppearancePatch.EMPTY }?.toBag(),
        enabled = enabled,
        filterConfig = filterConfig,
    )
