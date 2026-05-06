package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTargetRef
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
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.home.misskey.MissKeyLocalTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MissKeyPublicTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyHybridTimelinePresenter
import dev.dimension.flare.ui.presenter.list.AntennasTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ChannelTimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
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

    private val favouriteTimelineSpec =
        TimelineSpec(
            id = "misskey.favourite",
            title = UiStrings.Favourite,
            icon = UiIcon.Favourite.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                MisskeyFavouriteTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    private val hybridTimelineSpec =
        TimelineSpec(
            id = "misskey.hybrid",
            title = UiStrings.Social,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                MisskeyHybridTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    private val localTimelineSpec =
        TimelineSpec(
            id = "misskey.local",
            title = UiStrings.MastodonLocal,
            icon = UiIcon.Local.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                MissKeyLocalTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    private val globalTimelineSpec =
        TimelineSpec(
            id = "misskey.global",
            title = UiStrings.MastodonPublic,
            icon = UiIcon.World.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                MissKeyPublicTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    private val antennaTimelineSpec =
        TimelineSpec(
            id = "misskey.antenna",
            title = UiStrings.Antenna,
            icon = UiIcon.Rss.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            presenterFactory = {
                AntennasTimelinePresenter(
                    accountType = AccountType.Specific(it.accountKey),
                    id = it.resourceId,
                )
            },
        )

    private val channelTimelineSpec =
        TimelineSpec(
            id = "misskey.channel",
            title = UiStrings.Channel,
            icon = UiIcon.Channel.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            presenterFactory = {
                ChannelTimelinePresenter(
                    accountType = AccountType.Specific(it.accountKey),
                    id = it.resourceId,
                )
            },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.list,
            favouriteTimelineSpec,
            hybridTimelineSpec,
            localTimelineSpec,
            globalTimelineSpec,
            antennaTimelineSpec,
            channelTimelineSpec,
        )

    override fun defaultTabs(accountKey: MicroBlogKey): ImmutableList<TimelineTargetRef> =
        persistentListOf(
            CommonTimelineSpecs.home.target(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon(accountKey.host),
            ),
        )

    override fun shortcuts(accountKey: MicroBlogKey): ImmutableList<ShortcutSpec> =
        persistentListOf(
            ShortcutSpec(
                title = UiStrings.Favourite,
                icon = UiIcon.Favourite,
                target = ShortcutSpec.Target.Timeline(
                    favouriteTimelineSpec.target(TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.List,
                icon = UiIcon.List,
                target = ShortcutSpec.Target.Route(
                    DeeplinkRoute.AllLists(accountKey),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Social,
                icon = UiIcon.Featured,
                target = ShortcutSpec.Target.Timeline(
                    hybridTimelineSpec.target(TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.MastodonLocal,
                icon = UiIcon.Local,
                target = ShortcutSpec.Target.Timeline(
                    localTimelineSpec.target(TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.MastodonPublic,
                icon = UiIcon.World,
                target = ShortcutSpec.Target.Timeline(
                    globalTimelineSpec.target(TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Antenna,
                icon = UiIcon.Rss,
                target = ShortcutSpec.Target.Route(
                    DeeplinkRoute.Misskey.AllAntennas(accountKey),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Channel,
                icon = UiIcon.Channel,
                target = ShortcutSpec.Target.Route(
                    DeeplinkRoute.Misskey.AllChannels(accountKey),
                ),
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        MisskeyService("https://$host/api/").meta(MetaRequest()).render()

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
