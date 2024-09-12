package dev.dimension.flare.di

import androidx.media3.common.util.UnstableApi
import dev.dimension.flare.common.PlayerPoll
import dev.dimension.flare.data.repository.ComposeNotifyUseCase
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.VideoPlayerPool
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

@UnstableApi
val androidModule =
    module {
        singleOf(::ComposeNotifyUseCase)
        singleOf(::SettingsRepository)
        singleOf(::PlayerPoll)
        singleOf(::VideoPlayerPool)
    }
