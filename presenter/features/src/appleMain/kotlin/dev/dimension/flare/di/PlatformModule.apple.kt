package dev.dimension.flare.di

import dev.dimension.flare.common.AppleOnDeviceAI
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.ApplePlatformPathProducer
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.AppleAmberSignerBridge
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.shared.image.IosImageCompressor
import dev.dimension.flare.ui.humanizer.AppleFormatter
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.render.ApplePlatformTextRenderer
import dev.dimension.flare.ui.render.PlatformTextRendering
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        singleOf(::AppDataStore)
        singleOf(::DriverFactory)
        singleOf(::ApplePlatformPathProducer) bind PlatformPathProducer::class
        singleOf(::AppleFormatter) bind PlatformFormatter::class
        singleOf(::ApplePlatformTextRenderer) bind PlatformTextRendering::class
        singleOf(::IosImageCompressor) bind ImageCompressor::class
        singleOf(::AppleOnDeviceAI) bind OnDeviceAI::class
        singleOf(::AppleAmberSignerBridge) bind AmberSignerBridge::class
    }
