@file:OptIn(ExperimentalJsExport::class)

package dev.dimension.flare.web.shared

import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.model.PlatformRuntimeData
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.plugin.module.dsl.startKoin
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

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
            ),
        extraTimelineSpecs = RssTimelineSpecs.timelineSpecs,
    )
