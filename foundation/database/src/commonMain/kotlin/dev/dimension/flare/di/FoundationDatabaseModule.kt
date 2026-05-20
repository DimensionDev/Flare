package dev.dimension.flare.di

import dev.dimension.flare.data.database.provideAppDatabase
import dev.dimension.flare.data.database.provideCacheDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

public val foundationDatabaseModule: Module =
    module {
        single { provideAppDatabase(get()) }
        single { provideCacheDatabase(get()) }
    }
