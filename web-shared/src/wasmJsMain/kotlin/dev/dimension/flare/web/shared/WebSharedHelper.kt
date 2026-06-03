@file:OptIn(ExperimentalJsExport::class)

package dev.dimension.flare.web.shared

import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.ui.humanizer.WebFormatterBridge
import dev.dimension.flare.ui.humanizer.installWebFormatterBridge
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.plugin.module.dsl.startKoin

internal object WebSharedHelper {
    private var initialized = false

    fun initialize() {
        if (initialized) return
        startKoin<WebKoinApplication>()
        initialized = true
    }
}

@JsExport
@JsName("webSharedInitialize")
public fun webSharedInitialize() {
    WebSharedHelper.initialize()
}

@JsExport
@JsName("webSharedInstallFormatter")
public fun webSharedInstallFormatter(
    formatNumber: (Double) -> String,
    formatRelativeInstant: (Double) -> String,
    formatFullInstant: (Double) -> String,
    formatAbsoluteInstant: (Double) -> String,
) {
    installWebFormatterBridge(
        object : WebFormatterBridge {
            override fun formatNumber(number: Double): String = formatNumber(number)

            override fun formatRelativeInstant(epochMillis: Double): String = formatRelativeInstant(epochMillis)

            override fun formatFullInstant(epochMillis: Double): String = formatFullInstant(epochMillis)

            override fun formatAbsoluteInstant(epochMillis: Double): String = formatAbsoluteInstant(epochMillis)
        },
    )
}

@KoinApplication
internal class WebKoinApplication

@Module
@Configuration
@ComponentScan("dev.dimension.flare.web.shared")
internal class WebKoinModule

@Single
internal fun runtimeData(): PlatformRuntimeData =
    PlatformRuntimeData(
        platformSpecs =
            listOf(
                MastodonPlatformSpec,
                MisskeyPlatformSpec,
                BlueskyPlatformSpec,
            ),
        extraTimelineSpecs = RssTimelineSpecs.timelineSpecs,
    )
