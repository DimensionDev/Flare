package dev.dimension.flare.di

import dev.dimension.flare.data.platform.AllRssTimelineLoaderFactory
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Module
@Configuration
@ComponentScan("dev.dimension.flare.data.platform")
internal class SubscriptionKoinModule {
    @Single
    fun runtimeData(
        platformSpecs: List<PlatformSpec>,
        allRssTimelineLoaderFactory: AllRssTimelineLoaderFactory,
    ): PlatformRuntimeData =
        PlatformRuntimeData(
            platformSpecs = platformSpecs,
            extraTimelineSpecs = RssTimelineSpecs.timelineSpecs(allRssTimelineLoaderFactory),
        )
}
