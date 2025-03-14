package dev.dimension.flare.di

import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        single {
            AppDataStore(
                producePath = { fileName ->
                    "~/.config/Flare/$fileName"
                }
            )
        }
        singleOf(::DriverFactory)
    }
