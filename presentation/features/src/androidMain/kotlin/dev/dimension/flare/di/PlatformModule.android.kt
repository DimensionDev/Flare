package dev.dimension.flare.di

import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datasource.nostr.DatabaseNostrCache
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.AndroidPlatformPathProducer
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.nostr.AmberIntentLauncherRegistry
import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.AndroidAmberSignerBridge
import dev.dimension.flare.media.AndroidImageCompressor
import dev.dimension.flare.media.ImageCompressor
import dev.dimension.flare.ui.humanizer.AndroidFormatter
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import okio.FileSystem
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        single { AppDataStore(get<FileStorage>()) }
        singleOf(::DriverFactory)
        singleOf(::AmberIntentLauncherRegistry)
        single<AmberSignerBridge> { AndroidAmberSignerBridge(androidContext(), get()) }
        single<FileStorage> { OkioFileStorage(FileSystem.SYSTEM, AndroidPlatformPathProducer(androidContext())) }
        singleOf(::AndroidFormatter) bind PlatformFormatter::class
        singleOf(::AndroidImageCompressor) bind ImageCompressor::class
        single<NostrCache> { DatabaseNostrCache(get()) }
    }
