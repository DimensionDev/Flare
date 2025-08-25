package dev.dimension.flare.di

import dev.dimension.flare.data.repository.SettingsRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

public val composeUiModule: Module =
    module {
        singleOf(::SettingsRepository)
    }
