package dev.dimension.flare.di

import dev.dimension.flare.common.FossOnDeviceAI
import dev.dimension.flare.data.ai.OnDeviceAI
import org.koin.dsl.module

val aiModule =
    module {
        single<OnDeviceAI> { FossOnDeviceAI(get()) }
    }
