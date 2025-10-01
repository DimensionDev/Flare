package dev.dimension.flare.ui.controllers

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.data.network.rss.AppleWebScraper
import dev.dimension.flare.di.KoinHelper
import org.koin.core.context.startKoin
import org.koin.dsl.binds
import org.koin.dsl.module

public object ComposeUIHelper {
    public fun initialize(
        inAppNotification: InAppNotification,
        appleWebScraper: AppleWebScraper,
    ) {
        startKoin {
            modules(KoinHelper.modules())
            modules(
                module {
                    single {
                        inAppNotification
                    } binds arrayOf(InAppNotification::class)
                    single {
                        appleWebScraper
                    } binds arrayOf(AppleWebScraper::class)
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
