package dev.dimension.flare.di

import dev.dimension.flare.model.PlatformRegistry

internal fun appModule(platformRegistry: PlatformRegistry) = listOf(commonModule(platformRegistry), platformModule)
