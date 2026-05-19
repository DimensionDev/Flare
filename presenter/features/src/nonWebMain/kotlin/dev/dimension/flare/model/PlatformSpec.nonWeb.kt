package dev.dimension.flare.model

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.nostr.NostrDataSource
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance

internal actual val defaultSocialPlatformRegistry: SocialPlatformRegistry =
    SocialPlatformRegistry(
        listOf(NostrSocialPlatformPlugin) + defaultSocialPlatformPlugins,
    )

private data object NostrSocialPlatformPlugin : SocialPlatformPlugin {
    override val spec: PlatformSpec = NostrPlatformSpec

    override suspend fun recommendedInstances(): List<UiInstance> =
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

    override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Nostr)?.let {
            NostrDataSource(
                accountKey = it.accountKey,
            )
        }
}
