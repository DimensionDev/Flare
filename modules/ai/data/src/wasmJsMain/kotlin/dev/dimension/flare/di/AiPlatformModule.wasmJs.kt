package dev.dimension.flare.di

import dev.dimension.flare.common.WebOnDeviceAI
import dev.dimension.flare.data.ai.OnDeviceAI
import org.koin.core.module.Module

internal actual fun Module.registerPlatformAi() {
    single<OnDeviceAI> { WebOnDeviceAI }
}
