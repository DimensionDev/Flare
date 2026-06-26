package dev.dimension.flare.apple.shared

import co.touchlab.crashkios.crashlytics.enableCrashlytics
import co.touchlab.crashkios.crashlytics.setCrashlyticsUnhandledExceptionHook
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.SwiftOnDeviceAI
import dev.dimension.flare.data.platform.AllRssTimelineLoaderFactory
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
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

public object AppleSharedHelper {
    public fun setupCrashlytics() {
        enableCrashlytics()
        setCrashlyticsUnhandledExceptionHook()
    }

    public fun initialize(
        inAppNotification: InAppNotification,
        swiftFormatter: SwiftFormatter,
        swiftPlatformTextRenderer: SwiftPlatformTextRenderer,
        swiftOnDeviceAI: SwiftOnDeviceAI,
    ) {
        AppleBridgeDependencies.install(
            inAppNotification = inAppNotification,
            swiftFormatter = swiftFormatter,
            swiftPlatformTextRenderer = swiftPlatformTextRenderer,
            swiftOnDeviceAI = swiftOnDeviceAI,
        )
        startKoin<AppleKoinApplication>()
    }
}

@HiddenFromObjC
@KoinApplication
internal class AppleKoinApplication

@HiddenFromObjC
@Module
@Configuration
@ComponentScan("dev.dimension.flare.apple.shared")
internal class AppleKoinModule

@Single
internal fun runtimeData(allRssTimelineLoaderFactory: AllRssTimelineLoaderFactory): PlatformRuntimeData =
    PlatformRuntimeData(
        platformSpecs = platformSpecs(),
        extraTimelineSpecs = RssTimelineSpecs.timelineSpecs(allRssTimelineLoaderFactory),
    )

internal expect fun platformSpecs(): List<PlatformSpec>

@Single
internal fun inAppNotification(scope: CoroutineScope): InAppNotification =
    ProxyInAppNotification(
        delegate = AppleBridgeDependencies.inAppNotification(),
        scope = scope,
    )

@Single
internal fun swiftFormatter(): SwiftFormatter = AppleBridgeDependencies.swiftFormatter()

@Single
internal fun swiftPlatformTextRenderer(): SwiftPlatformTextRenderer = AppleBridgeDependencies.swiftPlatformTextRenderer()

@Single
internal fun swiftOnDeviceAI(): SwiftOnDeviceAI = AppleBridgeDependencies.swiftOnDeviceAI()

private object AppleBridgeDependencies {
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
        requireNotNull(inAppNotification) { "AppleSharedHelper.initialize must install InAppNotification before Koin starts." }

    fun swiftFormatter(): SwiftFormatter =
        requireNotNull(swiftFormatter) { "AppleSharedHelper.initialize must install SwiftFormatter before Koin starts." }

    fun swiftPlatformTextRenderer(): SwiftPlatformTextRenderer =
        requireNotNull(swiftPlatformTextRenderer) {
            "AppleSharedHelper.initialize must install SwiftPlatformTextRenderer before Koin starts."
        }

    fun swiftOnDeviceAI(): SwiftOnDeviceAI =
        requireNotNull(swiftOnDeviceAI) { "AppleSharedHelper.initialize must install SwiftOnDeviceAI before Koin starts." }
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
