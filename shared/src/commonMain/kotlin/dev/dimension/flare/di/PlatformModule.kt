package dev.dimension.flare.di

import dev.dimension.flare.data.database.DriverFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

expect val platformModule: Module