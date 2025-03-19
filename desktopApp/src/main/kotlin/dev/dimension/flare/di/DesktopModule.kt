package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.ui.component.platform.VideoPlayerPool
import org.koin.dsl.binds
import org.koin.dsl.module

val desktopModule =
    module {
        single {
            object : InAppNotification {
                override fun onProgress(
                    message: Message,
                    progress: Int,
                    total: Int,
                ) {
                }

                override fun onSuccess(message: Message) {
                }

                override fun onError(
                    message: Message,
                    throwable: Throwable,
                ) {
                }
            }
        } binds arrayOf(InAppNotification::class)
        single {
            VideoPlayerPool(
                get(),
            )
        }
    }
