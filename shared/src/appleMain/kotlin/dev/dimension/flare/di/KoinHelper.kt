package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.dsl.binds
import org.koin.dsl.module

object KoinHelper : KoinComponent {
    fun start(inAppNotification: InAppNotification) {
        startKoin {
            modules(appModule())
            modules(
                module {
                    single {
                        inAppNotification
                    } binds arrayOf(InAppNotification::class)
                },
            )
        }
    }
}
