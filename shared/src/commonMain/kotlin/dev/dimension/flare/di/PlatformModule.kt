package dev.dimension.flare.di

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import org.koin.core.module.Module

internal expect val platformModule: Module
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public object KoinHelper {
    public fun modules(): List<Module> = appModule()
}
