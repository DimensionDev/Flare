package dev.dimension.flare.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public val LocalGlobalAppearance: ProvidableCompositionLocal<GlobalAppearance> =
    staticCompositionLocalOf {
        error("No GlobalAppearance provided")
    }

@HiddenFromObjC
public val LocalTimelineAppearance: ProvidableCompositionLocal<TimelineAppearance> =
    staticCompositionLocalOf {
        error("No TimelineAppearance provided")
    }
