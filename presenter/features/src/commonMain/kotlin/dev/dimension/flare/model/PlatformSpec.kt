package dev.dimension.flare.model

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.datasource.nostr.NostrDataSource
import dev.dimension.flare.data.datasource.pleroma.PleromaDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.collections.immutable.ImmutableList

internal interface PlatformSpec : SocialPlatformSpec {
    val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>>

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>
}

internal val defaultSocialPlatformRegistry: SocialPlatformRegistry =
    SocialPlatformRegistry(
        listOf(
            NostrSocialPlatformPlugin,
            MastodonSocialPlatformPlugin,
            MisskeySocialPlatformPlugin,
            BlueskySocialPlatformPlugin,
            XqtSocialPlatformPlugin,
            VvoSocialPlatformPlugin,
        ),
    )

internal val SocialPlatformRegistry.platformSpecs: List<PlatformSpec>
    get() = specs.map { it as PlatformSpec }

internal fun SocialPlatformRegistry.requirePlatformSpec(type: PlatformType): PlatformSpec = requireSpec(type) as PlatformSpec

private data object NostrSocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = NostrPlatformSpec

    override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Nostr)?.let {
            NostrDataSource(
                accountKey = it.accountKey,
            )
        }
}

private data object MastodonSocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = MastodonPlatformSpec

    override fun createDataSource(account: UiAccount): MicroblogDataSource? =
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
}

private data object MisskeySocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = MisskeyPlatformSpec

    override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Misskey)?.let {
            MisskeyDataSource(
                accountKey = it.accountKey,
                host = it.host,
            )
        }
}

private data object BlueskySocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = BlueskyPlatformSpec

    override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Bluesky)?.let {
            BlueskyDataSource(
                accountKey = it.accountKey,
            )
        }
}

private data object XqtSocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = XqtPlatformSpec

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

public val PlatformType.icon: UiIcon
    get() = spec.metadata.icon

public fun PlatformType.agreementUrl(host: String): String? = spec.agreementUrl(host)

internal val PlatformType.spec: PlatformSpec
    get() = defaultSocialPlatformRegistry.requirePlatformSpec(this)
