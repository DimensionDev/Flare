package dev.dimension.flare.di

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.dimension.flare.common.PlayerPoll
import dev.dimension.flare.data.repository.ComposeNotifyUseCase
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.CacheDataSourceFactory
import dev.dimension.flare.ui.component.VideoPlayerPool
import dev.dimension.flare.ui.component.status.DefaultStatusEvent
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.bluesky.BlueskyStatusEvent
import dev.dimension.flare.ui.component.status.mastodon.MastodonStatusEvent
import dev.dimension.flare.ui.component.status.misskey.MisskeyStatusEvent
import dev.dimension.flare.ui.component.status.xqt.XQTStatusEvent
import org.koin.core.module.dsl.binds
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

@UnstableApi
val androidModule =
    module {
        singleOf(::ComposeNotifyUseCase)
        singleOf(::DefaultStatusEvent) withOptions {
            binds(
                listOf(
                    MastodonStatusEvent::class,
                    MisskeyStatusEvent::class,
                    BlueskyStatusEvent::class,
                    XQTStatusEvent::class,
                    StatusEvent::class,
                ),
            )
        }
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
