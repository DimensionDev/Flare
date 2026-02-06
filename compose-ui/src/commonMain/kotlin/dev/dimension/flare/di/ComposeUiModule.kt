package dev.dimension.flare.di

import dev.dimension.flare.data.repository.SettingsRepository
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

public val composeUiModule: Module =
    module {
        singleOf(::SettingsRepository)
        single(named("hideRepostsFlow")) {
            val repo: SettingsRepository = get()
            repo.appearanceSettings.map { it.hideReposts }
        }
    }
