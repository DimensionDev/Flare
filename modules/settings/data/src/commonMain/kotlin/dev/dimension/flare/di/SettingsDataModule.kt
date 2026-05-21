package dev.dimension.flare.di

import dev.dimension.flare.data.settings.TranslationSettingsProviderImpl
import dev.dimension.flare.data.translation.TranslationSettingsProvider
import org.koin.core.module.Module
import org.koin.dsl.module

public val settingsDataModule: Module =
    module {
        single<TranslationSettingsProvider> { TranslationSettingsProviderImpl(get()) }
    }
