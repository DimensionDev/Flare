package dev.dimension.flare.di

import org.koin.core.module.Module

internal expect val platformModule: Module

public object KoinHelper {
    public fun modules(): List<Module> = appModule()
}