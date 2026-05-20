package dev.dimension.flare.di

import dev.dimension.flare.data.ai.AiCompletionService
import dev.dimension.flare.data.ai.OpenAIService
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

public val aiDataModule: Module =
    module {
        singleOf(::OpenAIService)
        singleOf(::AiCompletionService)
    }
