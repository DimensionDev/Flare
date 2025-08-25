package dev.dimension.flare.di

import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.network.rss.NativeWebScraper
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        singleOf(::AppDataStore)
        singleOf(::DriverFactory)
        singleOf(::NativeWebScraper)
        singleOf(::PlatformPathProducer)
    }

public object KoinHelper {
    public fun modules(): List<Module> = appModule()
}
