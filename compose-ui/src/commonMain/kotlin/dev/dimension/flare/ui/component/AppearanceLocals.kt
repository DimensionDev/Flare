package dev.dimension.flare.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance

public val LocalGlobalAppearance: ProvidableCompositionLocal<GlobalAppearance> =
    staticCompositionLocalOf {
        error("No GlobalAppearance provided")
    }

public val LocalTimelineAppearance: ProvidableCompositionLocal<TimelineAppearance> =
    staticCompositionLocalOf {
        error("No TimelineAppearance provided")
    }

public val LocalAppSettings: ProvidableCompositionLocal<AppSettings> =
    staticCompositionLocalOf {
        AppSettings(version = "")
    }
