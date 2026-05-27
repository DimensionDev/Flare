package dev.dimension.flare.di

import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.PlatformRegistry
import org.koin.core.module.Module
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

internal expect val platformModule: Module

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public object KoinHelper {
    public fun modules(
        platformRegistry: PlatformRegistry,
        timelineSpecs: List<TimelineSpec<out TimelineSpec.Data>>,
    ): List<Module> = appModule(platformRegistry, timelineSpecs)
}
