package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.network.bluesky.BlueskyPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.SocialPlatformPlugin
import dev.dimension.flare.model.SocialPlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

public data object BlueskySocialPlatformPlugin : SocialPlatformPlugin {
    public override val spec: SocialPlatformSpec = BlueskySocialPlatformSpec

    public override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Bluesky)?.let {
            BlueskyDataSource(
                accountKey = it.accountKey,
            )
        }

    public override suspend fun recommendedInstances(): List<UiInstance> =
        listOf(
            UiInstance(
                name = "Bluesky",
                description =
                    "The web. Email. RSS feeds. XMPP chats. " +
                        "What all these technologies had in common is they allowed people to freely interact " +
                        "and create content, without a single intermediary.",
                iconUrl = null,
                domain = "bsky.social",
                type = PlatformType.Bluesky,
                bannerUrl = null,
                usersCount = 0,
            ),
        )
}

public data object BlueskySocialPlatformSpec : SocialPlatformSpec {
    public override val type: PlatformType = PlatformType.Bluesky
    public override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = BlueskyTimelineSpecs.timelineSpecs
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Bluesky",
            icon = UiIcon.Bluesky,
        )
    public override val detector: PlatformDetector = BlueskyPlatformDetector

    public override fun agreementUrl(host: String): String = "https://bsky.social/about/support/tos"

    public override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        buildList {
            add(DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$host/profile/{handle}")))
            add(DeepLinkPattern(DeepLinkMapping.Type.BlueskyPost.serializer(), Url("https://$host/profile/{handle}/post/{id}")))
            if (host == "bsky.social") {
                add(DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://bsky.app/profile/{handle}")))
                add(DeepLinkPattern(DeepLinkMapping.Type.BlueskyPost.serializer(), Url("https://bsky.app/profile/{handle}/post/{id}")))
            }
        }.toImmutableList()

    public override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    public override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
