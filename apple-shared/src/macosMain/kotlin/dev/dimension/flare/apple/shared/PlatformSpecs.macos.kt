package dev.dimension.flare.apple.shared

import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.FanboxPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.PixivPlatformSpec
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.model.PlatformSpec

internal actual fun platformSpecs(): List<PlatformSpec> =
    listOf(
        MastodonPlatformSpec,
        MisskeyPlatformSpec,
        BlueskyPlatformSpec,
        FanboxPlatformSpec,
        PixivPlatformSpec,
        XqtPlatformSpec,
        VvoPlatformSpec,
    )
