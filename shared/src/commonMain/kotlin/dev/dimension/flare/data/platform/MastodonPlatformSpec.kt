package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.guest.mastodon.GuestMastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Mastodon
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.network.mastodon.JoinMastodonService
import dev.dimension.flare.data.network.mastodon.MastodonInstanceService
import dev.dimension.flare.data.network.mastodon.MastodonPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.mapper.render
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object MastodonPlatformSpec : PlatformSpec {
    override val type = PlatformType.Mastodon
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Mastodon",
            icon = UiIcon.Mastodon,
        )
    override val detector: PlatformDetector = MastodonPlatformDetector

    override fun agreementUrl(host: String): String? = "https://$host/about"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        persistentListOf(
            DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$host/@{handle}")),
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/@{handle}/{id}")),
        )

    override fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            HomeTimelineTabItem(
                accountKey = accountKey,
                title = "Mastodon",
                icon = IconType.FavIcon(accountKey.host),
            ),
        )

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> =
        persistentListOf(
            Mastodon.LocalTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonLocal),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Local, accountKey),
                ),
            ),
            Mastodon.PublicTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonPublic),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.World, accountKey),
                ),
            ),
            Mastodon.BookmarkTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Bookmark),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Bookmark, accountKey),
                ),
            ),
            Mastodon.FavouriteTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Favourite),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Heart, accountKey),
                ),
            ),
            AllListTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.List),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.List, accountKey),
                ),
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata = MastodonInstanceService("https://$host/").instance().render()

    override suspend fun nodeList(): List<UiInstance> =
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

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        GuestMastodonDataSource(
            host = host,
            locale = locale,
        )
}
