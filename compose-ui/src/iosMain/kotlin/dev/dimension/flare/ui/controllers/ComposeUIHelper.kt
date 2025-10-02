package dev.dimension.flare.ui.controllers

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.di.KoinHelper
import org.koin.core.context.startKoin
import org.koin.dsl.binds
import org.koin.dsl.module

public object ComposeUIHelper {
    public fun initialize(
        inAppNotification: InAppNotification,
    ) {
        startKoin {
            modules(KoinHelper.modules())
            modules(
                module {
                    single {
                        inAppNotification
                    } binds arrayOf(InAppNotification::class)
                },
            )
            modules(dev.dimension.flare.di.composeUiModule)
        }
        SingletonImageLoader.setSafe { context ->
            ImageLoader
                .Builder(context)
                .crossfade(true)
                .build()
        }
    }
}
