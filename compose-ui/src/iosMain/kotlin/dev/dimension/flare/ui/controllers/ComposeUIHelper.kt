package dev.dimension.flare.ui.controllers

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.di.KoinHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.dsl.binds
import org.koin.dsl.module

public object ComposeUIHelper {
    public fun initialize(inAppNotification: InAppNotification) {
        startKoin {
            modules(KoinHelper.modules())
            modules(
                module {
                    single {
                        ProxyInAppNotification(inAppNotification, get())
                    } binds arrayOf(InAppNotification::class)
                },
            )
            modules(dev.dimension.flare.di.composeUiModule)
        }
        SingletonImageLoader.setSafe { context ->
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        KtorNetworkFetcherFactory(
                            httpClient =
                                ktorClient {
                                    useDefaultTransformers = false
                                },
                        ),
                    )
                }.crossfade(true)
                .build()
        }
    }
}

private class ProxyInAppNotification(
    private val delegate: InAppNotification,
    private val scope: CoroutineScope,
) : InAppNotification {
    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) {
        scope.launch {
            withContext(Dispatchers.Main) {
                delegate.onProgress(message, progress, total)
            }
        }
    }

    override fun onSuccess(message: Message) {
        scope.launch {
            withContext(Dispatchers.Main) {
                delegate.onSuccess(message)
            }
        }
    }

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) {
        scope.launch {
            withContext(Dispatchers.Main) {
                delegate.onError(message, throwable)
            }
        }
    }
}
