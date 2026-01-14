package dev.dimension.flare.di

import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.network.rss.NativeWebScraper
import dev.dimension.flare.shared.image.AndroidImageCompressor
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.humanizer.AndroidFormatter
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        singleOf(::AppDataStore)
        singleOf(::DriverFactory)
        singleOf(::NativeWebScraper)
        singleOf(::PlatformPathProducer)
        singleOf(::AndroidFormatter) bind PlatformFormatter::class
        singleOf(::AndroidImageCompressor) bind ImageCompressor::class
    }
