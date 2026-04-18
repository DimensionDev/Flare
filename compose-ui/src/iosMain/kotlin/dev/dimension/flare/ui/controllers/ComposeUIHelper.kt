package dev.dimension.flare.ui.controllers

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import dev.dimension.flare.common.GlobalConfig
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.SwiftOnDeviceAI
import dev.dimension.flare.data.database.migrateDatabase
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.ui.humanizer.SwiftFormatter
import dev.dimension.flare.ui.render.PlatformText
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.SwiftPlatformTextRenderer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSArray

public object ComposeUIHelper {
    @OptIn(ExperimentalCoilApi::class)
    public fun initialize(
        inAppNotification: InAppNotification,
        swiftFormatter: SwiftFormatter,
        swiftPlatformTextRenderer: SwiftPlatformTextRenderer,
        swiftOnDeviceAI: SwiftOnDeviceAI,
        isMainApp: Boolean,
    ) {
        if (isMainApp) {
            migrateDatabase()
        } else {
            GlobalConfig.disableLogging = true
        }
        startKoin {
            modules(KoinHelper.modules())
            modules(
                module {
                    single {
                        ProxyInAppNotification(inAppNotification, get())
                    } bind InAppNotification::class
                    single {
                        swiftFormatter
                    } bind SwiftFormatter::class
                    single {
                        swiftOnDeviceAI
                    } bind SwiftOnDeviceAI::class
                    single {
                        swiftPlatformTextRenderer
                    } bind SwiftPlatformTextRenderer::class
                },
            )
        }
        if (isMainApp) {
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

    public fun initializeLite() {
        GlobalConfig.disableLogging = true
        startKoin {
            modules(KoinHelper.modules())
            modules(
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
                    } bind InAppNotification::class
                    single {
                        object : SwiftFormatter {
                            override fun formatNumber(number: Long): String = number.toString()
                        }
                    } bind SwiftFormatter::class
                    single {
                        object : SwiftOnDeviceAI {
                            override suspend fun isAvailable(): Boolean = false

                            override suspend fun translate(
                                source: String,
                                targetLanguage: String,
                                prompt: String,
                            ): String? = null

                            override suspend fun tldr(
                                source: String,
                                targetLanguage: String,
                                prompt: String,
                            ): String? = null
                        }
                    } bind SwiftOnDeviceAI::class
                    single {
                        object : SwiftPlatformTextRenderer {
                            override fun render(renderRuns: ImmutableList<RenderContent>): PlatformText = NSArray()
                        }
                    } bind SwiftPlatformTextRenderer::class
                },
            )
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
