package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTargetRef
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.nostr.NostrPlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object NostrPlatformSpec : PlatformSpec {
    override val type = PlatformType.Nostr
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Nostr",
            icon = UiIcon.Nostr,
        )
    override val detector: PlatformDetector = NostrPlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> = persistentListOf()

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
        )

    override fun defaultTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTargetRef> =
        persistentListOf(
            CommonTimelineSpecs.home.target(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Nostr),
            ),
        )

    override fun shortcuts(accountKey: MicroBlogKey): ImmutableList<ShortcutSpec> = persistentListOf()

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
