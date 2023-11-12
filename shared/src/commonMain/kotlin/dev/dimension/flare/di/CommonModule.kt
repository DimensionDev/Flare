package dev.dimension.flare.di

import dev.dimension.flare.data.database.provideAppDatabase
import dev.dimension.flare.data.database.provideCacheDatabase
import dev.dimension.flare.data.database.provideVersionDatabase
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ApplicationRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val commonModule =
    module {
        singleOf(::AccountRepository)
        single { provideVersionDatabase(get()) }
        single { provideAppDatabase(get(), get()) }
        single { provideCacheDatabase(get(), get()) }
        singleOf(::ApplicationRepository)
    }
