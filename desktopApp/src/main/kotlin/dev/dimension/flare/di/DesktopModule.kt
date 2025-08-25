package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.ui.component.ComposeInAppNotification
import dev.dimension.flare.ui.component.platform.VideoPlayerPool
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module

val desktopModule =
    module {
        single { ComposeInAppNotification() } binds arrayOf(InAppNotification::class)
        singleOf(::VideoPlayerPool)
    }
