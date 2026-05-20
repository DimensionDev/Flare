package dev.dimension.flare.di

import dev.dimension.flare.data.database.cache.model.TranslationSettingsProvider
import dev.dimension.flare.data.settings.TranslationSettingsProviderImpl
import org.koin.core.module.Module
import org.koin.dsl.module

public val settingsDataModule: Module =
    module {
        single<TranslationSettingsProvider> { TranslationSettingsProviderImpl(get()) }
    }
