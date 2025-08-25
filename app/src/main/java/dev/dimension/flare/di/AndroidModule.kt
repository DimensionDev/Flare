package dev.dimension.flare.di

import androidx.media3.common.util.UnstableApi
import dev.dimension.flare.common.ComposeInAppNotification
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.PodcastManager
import dev.dimension.flare.common.VideoDownloadHelper
import dev.dimension.flare.ui.component.VideoPlayerPool
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module

@UnstableApi
val androidModule =
    module {
        singleOf(::VideoPlayerPool)
        singleOf(::ComposeInAppNotification) binds arrayOf(InAppNotification::class, ComposeInAppNotification::class)
        singleOf(::VideoDownloadHelper)
        singleOf(::PodcastManager)
    }
