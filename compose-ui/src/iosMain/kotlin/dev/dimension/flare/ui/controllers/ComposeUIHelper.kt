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
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.ui.humanizer.SwiftFormatter
import dev.dimension.flare.ui.render.SwiftPlatformTextRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.plugin.module.dsl.startKoin

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
        IosBridgeDependencies.install(
            inAppNotification = inAppNotification,
            swiftFormatter = swiftFormatter,
            swiftPlatformTextRenderer = swiftPlatformTextRenderer,
            swiftOnDeviceAI = swiftOnDeviceAI,
        )
        startKoin<IosKoinApplication>()
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

@KoinApplication
internal class IosKoinApplication

@Module
@Configuration
@ComponentScan("dev.dimension.flare.ui.controllers")
internal class IosKoinModule

@Single
internal fun runtimeData(): PlatformRuntimeData =
    PlatformRuntimeData(
        platformSpecs =
            listOf(
                NostrPlatformSpec,
                MastodonPlatformSpec,
                MisskeyPlatformSpec,
                BlueskyPlatformSpec,
                XqtPlatformSpec,
                VvoPlatformSpec,
            ),
        extraTimelineSpecs = RssTimelineSpecs.timelineSpecs,
    )

@Single
internal fun inAppNotification(scope: CoroutineScope): InAppNotification =
    ProxyInAppNotification(
        delegate = IosBridgeDependencies.inAppNotification(),
        scope = scope,
    )

@Single
internal fun swiftFormatter(): SwiftFormatter = IosBridgeDependencies.swiftFormatter()

@Single
internal fun swiftPlatformTextRenderer(): SwiftPlatformTextRenderer = IosBridgeDependencies.swiftPlatformTextRenderer()

@Single
internal fun swiftOnDeviceAI(): SwiftOnDeviceAI = IosBridgeDependencies.swiftOnDeviceAI()

private object IosBridgeDependencies {
    private var inAppNotification: InAppNotification? = null
    private var swiftFormatter: SwiftFormatter? = null
    private var swiftPlatformTextRenderer: SwiftPlatformTextRenderer? = null
    private var swiftOnDeviceAI: SwiftOnDeviceAI? = null

    fun install(
        inAppNotification: InAppNotification,
        swiftFormatter: SwiftFormatter,
        swiftPlatformTextRenderer: SwiftPlatformTextRenderer,
        swiftOnDeviceAI: SwiftOnDeviceAI,
    ) {
        this.inAppNotification = inAppNotification
        this.swiftFormatter = swiftFormatter
        this.swiftPlatformTextRenderer = swiftPlatformTextRenderer
        this.swiftOnDeviceAI = swiftOnDeviceAI
    }

    fun inAppNotification(): InAppNotification =
        requireNotNull(inAppNotification) { "ComposeUIHelper.initialize must install InAppNotification before Koin starts." }

    fun swiftFormatter(): SwiftFormatter =
        requireNotNull(swiftFormatter) { "ComposeUIHelper.initialize must install SwiftFormatter before Koin starts." }

    fun swiftPlatformTextRenderer(): SwiftPlatformTextRenderer =
        requireNotNull(swiftPlatformTextRenderer) {
            "ComposeUIHelper.initialize must install SwiftPlatformTextRenderer before Koin starts."
        }

    fun swiftOnDeviceAI(): SwiftOnDeviceAI =
        requireNotNull(swiftOnDeviceAI) { "ComposeUIHelper.initialize must install SwiftOnDeviceAI before Koin starts." }
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
