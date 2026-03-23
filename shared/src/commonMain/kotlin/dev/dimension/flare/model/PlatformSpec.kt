package dev.dimension.flare.model

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList

internal interface PlatformSpec {
    val type: PlatformType
    val metadata: PlatformTypeMetadata
    val detector: PlatformDetector

    fun agreementUrl(host: String): String?

    fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>

    fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem>

    suspend fun instanceMetadata(host: String): UiInstanceMetadata

    fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource
}

public val PlatformType.logoUrl: String
    get() = spec.metadata.logoUrl

public val PlatformType.icon: dev.dimension.flare.ui.model.UiIcon
    get() = spec.metadata.icon

public fun PlatformType.agreementUrl(host: String): String? = spec.agreementUrl(host)

internal val PlatformType.spec: PlatformSpec
    get() =
        when (this) {
            PlatformType.Nostr -> NostrPlatformSpec
            PlatformType.Mastodon -> MastodonPlatformSpec
            PlatformType.Misskey -> MisskeyPlatformSpec
            PlatformType.Bluesky -> BlueskyPlatformSpec
            PlatformType.xQt -> XqtPlatformSpec
            PlatformType.VVo -> VvoPlatformSpec
        }
