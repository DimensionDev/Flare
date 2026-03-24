package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.network.bluesky.BlueskyPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal data object BlueskyPlatformSpec : PlatformSpec {
    override val type = PlatformType.Bluesky
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Bluesky",
            icon = UiIcon.Bluesky,
        )
    override val detector: PlatformDetector = BlueskyPlatformDetector

    override fun agreementUrl(host: String): String? = "https://bsky.social/about/support/tos"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        buildList {
            add(DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$host/profile/{handle}")))
            add(DeepLinkPattern(DeepLinkMapping.Type.BlueskyPost.serializer(), Url("https://$host/profile/{handle}/post/{id}")))
            if (host == "bsky.social") {
                add(DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://bsky.app/profile/{handle}")))
                add(DeepLinkPattern(DeepLinkMapping.Type.BlueskyPost.serializer(), Url("https://bsky.app/profile/{handle}/post/{id}")))
            }
        }.toImmutableList()

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> =
        persistentListOf(
            AllListTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.List),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.List, accountKey),
                ),
            ),
            Bluesky.FeedsTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Feeds),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Feeds, accountKey),
                ),
            ),
            Bluesky.BookmarkTimelineTabItem(AccountType.Specific(accountKey)),
            DirectMessageTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.DirectMessage),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Messages, accountKey),
                ),
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
