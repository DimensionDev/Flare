package dev.dimension.flare.ui.controllers

import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.ExperimentalComposeUiApi
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.SwiftOnDeviceAI
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.ui.humanizer.SwiftFormatter
import dev.dimension.flare.ui.render.SwiftPlatformTextRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module

public object ComposeUIHelper {
    @OptIn(
        ExperimentalCoilApi::class,
        ExperimentalComposeUiApi::class,
        ExperimentalComposeApi::class,
    )
    public fun initialize(
        inAppNotification: InAppNotification,
        swiftFormatter: SwiftFormatter,
        swiftPlatformTextRenderer: SwiftPlatformTextRenderer,
        swiftOnDeviceAI: SwiftOnDeviceAI,
    ) {
        val registry = supportedPlatformRegistry()
        val timelineSpecs = registry.all.flatMap { it.timelineSpecs } + RssTimelineSpecs.timelineSpecs
        startKoin {
            modules(KoinHelper.modules(registry, timelineSpecs))
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
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
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

private fun supportedPlatformRegistry(): PlatformRegistry =
    PlatformRegistry(
        listOf(
            NostrPlatformSpec,
            MastodonPlatformSpec,
            MisskeyPlatformSpec,
            BlueskyPlatformSpec,
            XqtPlatformSpec,
            VvoPlatformSpec,
        ),
    )

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
