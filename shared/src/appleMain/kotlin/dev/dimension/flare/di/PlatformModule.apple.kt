package dev.dimension.flare.di

import dev.dimension.flare.data.database.DriverFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        singleOf(::DriverFactory)
    }
