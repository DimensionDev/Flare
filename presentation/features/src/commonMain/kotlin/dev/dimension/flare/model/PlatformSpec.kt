package dev.dimension.flare.model

import dev.dimension.flare.data.platform.BlueskySocialPlatformPlugin
import dev.dimension.flare.data.platform.MastodonSocialPlatformPlugin
import dev.dimension.flare.data.platform.MisskeySocialPlatformPlugin
import dev.dimension.flare.data.platform.VvoSocialPlatformPlugin
import dev.dimension.flare.data.platform.XqtSocialPlatformPlugin

internal expect val defaultSocialPlatformRegistry: SocialPlatformRegistry

internal val defaultSocialPlatformPlugins: List<SocialPlatformPlugin> =
    listOf(
        MastodonSocialPlatformPlugin,
        MisskeySocialPlatformPlugin,
        BlueskySocialPlatformPlugin,
        XqtSocialPlatformPlugin,
        VvoSocialPlatformPlugin,
    )
