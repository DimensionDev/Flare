package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.network.misskey.JoinMisskeyService
import dev.dimension.flare.data.network.misskey.MisskeyPlatformDetector
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
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

internal data object MisskeyPlatformSpec : PlatformSpec {
    override val type = PlatformType.Misskey
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Misskey",
            icon = UiIcon.Misskey,
        )
    override val detector: PlatformDetector = MisskeyPlatformDetector

    override fun agreementUrl(host: String): String? = "https://$host/about"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        persistentListOf(
            DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$host/@{handle}")),
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/notes/{id}")),
        )

    override fun defaultTimelineTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTabItem> =
        persistentListOf(
            HomeTimelineTabItem(
                accountKey = accountKey,
                title = "Misskey",
                icon = IconType.FavIcon(accountKey.host),
            ),
        )

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> =
        persistentListOf(
            Misskey.FavouriteTimelineTabItem(
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
            Misskey.HybridTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Social),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Featured, accountKey),
                ),
            ),
            Misskey.LocalTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonLocal),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Local, accountKey),
                ),
            ),
            Misskey.GlobalTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonPublic),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.World, accountKey),
                ),
            ),
            Misskey.AntennasListTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Antenna),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Rss, accountKey),
                ),
            ),
            Misskey.ChannelListTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Channel),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Channel, accountKey),
                ),
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        MisskeyService("https://$host/api/").meta(MetaRequest()).render()

    override suspend fun nodeList(): List<UiInstance> =
        JoinMisskeyService.instances().instancesInfos.map {
            UiInstance(
                name = it.name,
                description = it.description,
                iconUrl = it.meta?.iconURL,
                domain = it.url,
                type = PlatformType.Misskey,
                bannerUrl = it.meta?.bannerURL,
                usersCount =
                    it.stats?.usersCount ?: it.nodeinfo
                        ?.usage
                        ?.users
                        ?.total ?: 0,
            )
        }

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
