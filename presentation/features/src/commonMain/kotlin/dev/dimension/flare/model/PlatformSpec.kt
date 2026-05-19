package dev.dimension.flare.model

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MastodonSocialPlatformPlugin
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.MisskeySocialPlatformPlugin
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
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
        else -> this as PlatformSpec
    }

private data object BlueskySocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = BlueskyPlatformSpec

    override suspend fun recommendedInstances(): List<UiInstance> =
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

    override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Bluesky)?.let {
            BlueskyDataSource(
                accountKey = it.accountKey,
            )
        }
}

private data object XqtSocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = XqtPlatformSpec

    override suspend fun recommendedInstances(): List<UiInstance> =
        listOf(
            UiInstance(
                name = "X",
                description =
                    "From breaking news and entertainment to sports and politics," +
                        " get the full story with all the live commentary.",
                iconUrl = null,
                domain = "x.com",
                type = PlatformType.xQt,
                bannerUrl = null,
                usersCount = 0,
            ),
        )

    override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.XQT)?.let {
            XQTDataSource(
                accountKey = it.accountKey,
            )
        }
}

private data object VvoSocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = VvoPlatformSpec

    override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.VVo)?.let {
            VVODataSource(
                accountKey = it.accountKey,
            )
        }
}
