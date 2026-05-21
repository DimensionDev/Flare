package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.nostr.NostrDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.nostr.NostrPlatformDetector
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data object NostrPlatformSpec : PlatformSpec {
    public override val type: PlatformType = PlatformType.Nostr
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Nostr",
            icon = UiIcon.Nostr,
        )
    override val detector: PlatformDetector = NostrPlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun restoreAccount(
        accountKey: MicroBlogKey,
        credentialJson: String,
    ): UiAccount =
        UiAccount.Nostr(
            accountKey = accountKey,
        )

    override fun createDataSource(account: UiAccount): MicroblogDataSource {
        require(account is UiAccount.Nostr) {
            "Expected Nostr account for ${type.name}, got ${account.platformType.name}"
        }
        return NostrDataSource(
            accountKey = account.accountKey,
        )
    }

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
