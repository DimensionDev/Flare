package dev.dimension.flare.di

import dev.dimension.flare.data.platform.AllRssTimelineLoaderFactory
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.FanboxPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.data.platform.PixivPlatformSpec
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.model.PlatformRuntimeData
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication
internal class DesktopKoinApplication

@Module
@Configuration
@ComponentScan("dev.dimension.flare.common", "dev.dimension.flare.di", "dev.dimension.flare.ui.component")
internal class DesktopKoinModule

@Single
internal fun runtimeData(allRssTimelineLoaderFactory: AllRssTimelineLoaderFactory): PlatformRuntimeData =
    PlatformRuntimeData(
        platformSpecs =
            listOf(
                NostrPlatformSpec,
                MastodonPlatformSpec,
                MisskeyPlatformSpec,
                BlueskyPlatformSpec,
                FanboxPlatformSpec,
                PixivPlatformSpec,
                XqtPlatformSpec,
                VvoPlatformSpec,
            ),
        extraTimelineSpecs = RssTimelineSpecs.timelineSpecs(allRssTimelineLoaderFactory),
    )
