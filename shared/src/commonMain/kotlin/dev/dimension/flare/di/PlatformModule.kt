package dev.dimension.flare.di

import org.koin.core.module.Module
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

internal expect val platformModule: Module

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public object KoinHelper {
    public fun modules(): List<Module> = appModule()
}
