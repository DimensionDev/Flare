package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.common.tryRun
import dev.dimension.flare.data.datasource.guest.mastodon.GuestMastodonDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.pleroma.PleromaDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.network.mastodon.JoinMastodonService
import dev.dimension.flare.data.network.mastodon.MastodonInstanceService
import dev.dimension.flare.data.network.mastodon.MastodonPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.SocialPlatformPlugin
import dev.dimension.flare.model.SocialPlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.mapper.render
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data object MastodonSocialPlatformPlugin : SocialPlatformPlugin {
    public override val spec: SocialPlatformSpec = MastodonSocialPlatformSpec

    public override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Mastodon)?.let {
            when (it.forkType) {
                UiAccount.Mastodon.Credential.ForkType.Mastodon -> {
                    MastodonDataSource(
                        accountKey = it.accountKey,
                        instance = it.instance,
                    )
                }

                UiAccount.Mastodon.Credential.ForkType.Pleroma -> {
                    PleromaDataSource(
                        accountKey = it.accountKey,
                        instance = it.instance,
                    )
                }
            }
        }

    public override suspend fun recommendedInstances(): List<UiInstance> {
        val instances =
            tryRun {
                JoinMastodonService.servers().map {
                    UiInstance(
                        name = it.domain,
                        description = it.description,
                        iconUrl = null,
                        domain = it.domain,
                        type = PlatformType.Mastodon,
                        bannerUrl = it.proxiedThumbnail,
                        usersCount = it.totalUsers,
                    )
                }
            }.getOrDefault(emptyList())
        val pawoo =
            tryRun {
                MastodonInstanceService("https://pawoo.net/").instance().let {
                    UiInstance(
                        name = it.domain ?: "pawoo.net",
                        description = it.title,
                        iconUrl = it.thumbnail?.url,
                        domain = it.domain ?: "pawoo.net",
                        type = PlatformType.Mastodon,
                        bannerUrl = it.thumbnail?.url,
                        usersCount = it.usage?.users?.activeMonth ?: 0,
                    )
                }
            }.getOrNull()
        val pinned =
            listOf(
                instances.firstOrNull { it.domain == "mstdn.jp" } ?: mastodonFallback("mstdn.jp"),
                instances.firstOrNull { it.domain == "pawoo.net" } ?: pawoo ?: mastodonFallback("pawoo.net"),
            )
        return pinned + instances.sortedByDescending { it.usersCount }.filter { it !in pinned }
    }
}

public data object MastodonSocialPlatformSpec : SocialPlatformSpec {
    public override val type: PlatformType = PlatformType.Mastodon
    public override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = MastodonTimelineSpecs.timelineSpecs
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Mastodon",
            icon = UiIcon.Mastodon,
        )
    public override val detector: PlatformDetector = MastodonPlatformDetector

    public override fun agreementUrl(host: String): String = "https://$host/about"

    public override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        persistentListOf(
            DeepLinkPattern(
                DeepLinkMapping.Type.Profile.serializer(),
                Url("https://$host/@{handle}"),
            ),
            DeepLinkPattern(
                DeepLinkMapping.Type.Post.serializer(),
                Url("https://$host/@{handle}/{id}"),
            ),
        )

    public override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        MastodonInstanceService("https://$host/").instance().render()

    public override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        GuestMastodonDataSource(
            host = host,
            locale = locale,
        )
}

private fun mastodonFallback(domain: String): UiInstance =
    UiInstance(
        name = domain,
        description = domain,
        iconUrl = null,
        domain = domain,
        type = PlatformType.Mastodon,
        bannerUrl = null,
        usersCount = 0,
    )
