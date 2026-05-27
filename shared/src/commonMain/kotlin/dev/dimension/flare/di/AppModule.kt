package dev.dimension.flare.di

import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.PlatformRegistry

internal fun appModule(
    platformRegistry: PlatformRegistry,
    timelineSpecs: List<TimelineSpec<out TimelineSpec.Data>>,
) = listOf(commonModule(platformRegistry, timelineSpecs), platformModule)
