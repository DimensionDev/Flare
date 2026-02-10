package dev.dimension.flare.di

import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.SettingsTimelineFilterRepository
import dev.dimension.flare.data.repository.TimelineFilterRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

public val composeUiModule: Module =
    module {
        singleOf(::SettingsRepository)
        singleOf(::SettingsTimelineFilterRepository) bind TimelineFilterRepository::class
    }
