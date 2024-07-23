package dev.dimension.flare.di

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.dimension.flare.common.PlayerPoll
import dev.dimension.flare.data.repository.ComposeNotifyUseCase
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.CacheDataSourceFactory
import dev.dimension.flare.ui.component.VideoPlayerPool
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

@UnstableApi
val androidModule =
    module {
        singleOf(::ComposeNotifyUseCase)
        singleOf(::SettingsRepository)
        singleOf(::PlayerPoll)
        single<ProgressiveMediaSource.Factory> {
            ProgressiveMediaSource.Factory(
                CacheDataSourceFactory(
                    get(),
                    100 * 1024 * 1024L,
                ),
            )
        }
        singleOf(::VideoPlayerPool)
    }
