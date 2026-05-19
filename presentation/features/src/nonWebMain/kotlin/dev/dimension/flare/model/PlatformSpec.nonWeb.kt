package dev.dimension.flare.model

import dev.dimension.flare.data.platform.NostrSocialPlatformPlugin

internal actual val defaultSocialPlatformRegistry: SocialPlatformRegistry =
    SocialPlatformRegistry(
        listOf(NostrSocialPlatformPlugin) + defaultSocialPlatformPlugins,
    )
