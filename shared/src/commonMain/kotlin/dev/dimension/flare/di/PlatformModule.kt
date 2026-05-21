package dev.dimension.flare.di

import dev.dimension.flare.model.PlatformRegistry
import org.koin.core.module.Module
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

internal expect val platformModule: Module

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public object KoinHelper {
    public fun modules(platformRegistry: PlatformRegistry): List<Module> = appModule(platformRegistry)
}
