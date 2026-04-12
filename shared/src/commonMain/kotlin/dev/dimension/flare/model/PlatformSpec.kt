package dev.dimension.flare.model

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal interface PlatformSpec {
    val type: PlatformType
    val metadata: PlatformTypeMetadata
    val detector: PlatformDetector

    fun agreementUrl(host: String): String?

    fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>

    fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            HomeTimelineTabItem(accountType = AccountType.Specific(accountKey)),
        )

    fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem>

    suspend fun instanceMetadata(host: String): UiInstanceMetadata

    suspend fun nodeList(): List<UiInstance> = emptyList()

    fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource
}

public val PlatformType.icon: UiIcon
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
