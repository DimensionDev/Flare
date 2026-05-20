package dev.dimension.flare.di

import dev.dimension.flare.data.local.LocalFilterRepository
import dev.dimension.flare.data.local.SearchHistoryRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

public val localDataModule: Module =
    module {
        singleOf(::LocalFilterRepository)
        singleOf(::SearchHistoryRepository)
    }
