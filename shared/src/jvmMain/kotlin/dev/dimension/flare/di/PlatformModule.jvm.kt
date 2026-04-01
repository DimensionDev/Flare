package dev.dimension.flare.di

import dev.dimension.flare.common.JvmOnDeviceAI
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.JvmPlatformPathProducer
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.JvmAmberSignerBridge
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.shared.image.JvmImageCompressor
import dev.dimension.flare.ui.humanizer.JVMFormatter
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        singleOf(::AppDataStore)
        singleOf(::DriverFactory)
        singleOf(::JvmPlatformPathProducer) bind PlatformPathProducer::class
        singleOf(::JVMFormatter) bind PlatformFormatter::class
        singleOf(::JvmImageCompressor) bind ImageCompressor::class
        singleOf(::JvmOnDeviceAI) bind OnDeviceAI::class
        singleOf(::JvmAmberSignerBridge) bind AmberSignerBridge::class
    }
