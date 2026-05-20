package dev.dimension.flare.di

import dev.dimension.flare.common.JvmOnDeviceAI
import dev.dimension.flare.data.ai.OnDeviceAI
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datasource.nostr.DatabaseNostrCache
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.io.createFileStorage
import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.JvmAmberSignerBridge
import dev.dimension.flare.media.ImageCompressor
import dev.dimension.flare.media.JvmImageCompressor
import dev.dimension.flare.ui.humanizer.JVMFormatter
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        single { AppDataStore(get<FileStorage>()) }
        singleOf(::DriverFactory)
        single<FileStorage> { createFileStorage() }
        singleOf(::JVMFormatter) bind PlatformFormatter::class
        singleOf(::JvmImageCompressor) bind ImageCompressor::class
        singleOf(::JvmOnDeviceAI) bind OnDeviceAI::class
        singleOf(::JvmAmberSignerBridge) bind AmberSignerBridge::class
        single<NostrCache> { DatabaseNostrCache(get()) }
    }
