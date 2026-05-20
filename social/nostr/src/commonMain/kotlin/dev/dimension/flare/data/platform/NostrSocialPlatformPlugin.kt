package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.CommonTimelineSpecs
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.nostr.NostrDataSource
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.nostr.NostrPlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.SocialPlatformPlugin
import dev.dimension.flare.model.SocialPlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data object NostrSocialPlatformPlugin : SocialPlatformPlugin {
    public override val spec: SocialPlatformSpec = NostrSocialPlatformSpec

    public override suspend fun recommendedInstances(): List<UiInstance> =
        listOf(
            UiInstance(
                name = "Nostr",
                description =
                    "A decentralized network based on cryptographic keypairs and that is not peer-to-peer, " +
                        "it is super simple and scalable and therefore has a chance of working.",
                iconUrl = null,
                domain = "nostr",
                type = PlatformType.Nostr,
                bannerUrl = null,
                usersCount = 0,
            ),
        )

    public override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Nostr)?.let {
            NostrDataSource(
                accountKey = it.accountKey,
            )
        }
}

public data object NostrSocialPlatformSpec : SocialPlatformSpec {
    public override val type: PlatformType = PlatformType.Nostr
    public override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(CommonTimelineSpecs.home)
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Nostr",
            icon = UiIcon.Nostr,
        )
    public override val detector: PlatformDetector = NostrPlatformDetector

    public override fun agreementUrl(host: String): String? = null

    public override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> = persistentListOf()

    public override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    public override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
