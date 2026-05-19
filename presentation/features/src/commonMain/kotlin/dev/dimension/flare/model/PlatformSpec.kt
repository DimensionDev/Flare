package dev.dimension.flare.model

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.platform.BlueskySocialPlatformPlugin
import dev.dimension.flare.data.platform.MastodonSocialPlatformPlugin
import dev.dimension.flare.data.platform.MisskeySocialPlatformPlugin
import dev.dimension.flare.data.platform.VvoSocialPlatformPlugin
import dev.dimension.flare.data.platform.XqtSocialPlatformPlugin
import kotlinx.collections.immutable.ImmutableList

internal interface PlatformSpec : SocialPlatformSpec {
    val legacyTimelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>>

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>
}

internal expect val defaultSocialPlatformRegistry: SocialPlatformRegistry

internal val defaultSocialPlatformPlugins: List<SocialPlatformPlugin> =
    listOf(
        MastodonSocialPlatformPlugin,
        MisskeySocialPlatformPlugin,
        BlueskySocialPlatformPlugin,
        XqtSocialPlatformPlugin,
        VvoSocialPlatformPlugin,
    )
