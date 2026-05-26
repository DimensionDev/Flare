package dev.dimension.flare

import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.model.PlatformRegistry

internal fun testPlatformRegistry(): PlatformRegistry =
    PlatformRegistry(
        listOf(
            NostrPlatformSpec,
            MastodonPlatformSpec,
            MisskeyPlatformSpec,
            BlueskyPlatformSpec,
            XqtPlatformSpec,
        ),
    )
