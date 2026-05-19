package dev.dimension.flare.model

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.BlueskySocialPlatformPlugin
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MastodonSocialPlatformPlugin
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.MisskeySocialPlatformPlugin
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.VvoSocialPlatformPlugin
import dev.dimension.flare.data.platform.XqtPlatformSpec
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

internal val SocialPlatformRegistry.platformSpecs: List<PlatformSpec>
    get() = specs.map { it.toPresentationPlatformSpec() }

internal fun SocialPlatformRegistry.requirePlatformSpec(type: PlatformType): PlatformSpec = requireSpec(type).toPresentationPlatformSpec()

private fun SocialPlatformSpec.toPresentationPlatformSpec(): PlatformSpec =
    when (type) {
        PlatformType.Mastodon -> MastodonPlatformSpec
        PlatformType.Misskey -> MisskeyPlatformSpec
        PlatformType.Bluesky -> BlueskyPlatformSpec
        PlatformType.xQt -> XqtPlatformSpec
        PlatformType.VVo -> VvoPlatformSpec
        else -> this as PlatformSpec
    }
