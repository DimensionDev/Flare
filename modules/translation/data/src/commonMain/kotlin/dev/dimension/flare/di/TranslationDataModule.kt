package dev.dimension.flare.di

import dev.dimension.flare.data.translation.OnlinePreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import org.koin.core.module.Module
import org.koin.dsl.module

public val translationDataModule: Module =
    module {
        single<PreTranslationService> { OnlinePreTranslationService(get(), get(), get(), get()) }
    }
