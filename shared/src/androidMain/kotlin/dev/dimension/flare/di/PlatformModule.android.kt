package dev.dimension.flare.di

import androidx.datastore.dataStoreFile
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.network.rss.NativeWebScraper
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        single {
            val context = androidContext()
            AppDataStore {
                context.dataStoreFile(it).absolutePath
            }
        }
        singleOf(::DriverFactory)
        singleOf(::NativeWebScraper)
    }

public object KoinHelper {
    public fun modules(): List<Module> = appModule()
}
