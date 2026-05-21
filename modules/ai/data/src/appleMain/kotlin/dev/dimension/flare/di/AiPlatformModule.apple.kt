package dev.dimension.flare.di

import dev.dimension.flare.common.AppleOnDeviceAI
import dev.dimension.flare.data.ai.OnDeviceAI
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind

internal actual fun Module.registerPlatformAi() {
    singleOf(::AppleOnDeviceAI) bind OnDeviceAI::class
}
