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
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList

internal interface PlatformSpec {
    val type: PlatformType
    val metadata: PlatformTypeMetadata
    val detector: PlatformDetector
    val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>>

    fun agreementUrl(host: String): String?

    fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>

    suspend fun instanceMetadata(host: String): UiInstanceMetadata

    fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource
}

internal interface SocialPlatformPlugin {
    val spec: PlatformSpec

    fun createDataSource(account: UiAccount): MicroblogDataSource?
}

internal class SocialPlatformRegistry(
    plugins: List<SocialPlatformPlugin>,
) {
    private val plugins = plugins.distinctBy { it.spec.type }
    private val specsByType = this.plugins.associateBy { it.spec.type }

    val specs: List<PlatformSpec>
        get() = plugins.map { it.spec }

    val loginPlatformTypes: List<PlatformType>
        get() = specs.map { it.type }

    fun requireSpec(type: PlatformType): PlatformSpec =
        requireNotNull(specsByType[type]?.spec) {
            "No social platform registered for $type"
        }

    fun createDataSource(account: UiAccount): MicroblogDataSource =
        plugins.firstNotNullOfOrNull { it.createDataSource(account) }
            ?: error("No social platform data source registered for ${account.platformType}")

    fun guestDataSource(
        type: PlatformType,
        host: String,
        locale: String,
    ): MicroblogDataSource =
        requireSpec(type).guestDataSource(
            host = host,
            locale = locale,
        )

    companion object {
        val default: SocialPlatformRegistry =
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
    }
}

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
    get() = SocialPlatformRegistry.default.requireSpec(this)
