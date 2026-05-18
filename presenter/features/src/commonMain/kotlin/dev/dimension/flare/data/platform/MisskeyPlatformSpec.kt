package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.misskey.MissKeyLocalTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MissKeyPublicTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyHybridTimelinePresenter
import dev.dimension.flare.ui.presenter.list.AntennasTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ChannelTimelinePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object MisskeyPlatformSpec : PlatformSpec {
    override val type = MisskeySocialPlatformSpec.type
    override val metadata = MisskeySocialPlatformSpec.metadata
    override val detector = MisskeySocialPlatformSpec.detector

    override fun agreementUrl(host: String): String? = MisskeySocialPlatformSpec.agreementUrl(host)

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        MisskeySocialPlatformSpec.deepLinkPatterns(host)

    internal val favouriteTimelineSpec =
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

    internal val hybridTimelineSpec =
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

    internal val localTimelineSpec =
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

    internal val globalTimelineSpec =
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

    internal val antennaTimelineSpec =
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

    internal val channelTimelineSpec =
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
            CommonTimelineSpecs.discover,
            CommonTimelineSpecs.list,
            favouriteTimelineSpec,
            hybridTimelineSpec,
            localTimelineSpec,
            globalTimelineSpec,
            antennaTimelineSpec,
            channelTimelineSpec,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        MisskeySocialPlatformSpec.instanceMetadata(host)

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}

internal fun UiList.Antenna.toTimelineTabItemV2(accountKey: MicroBlogKey): TimelineTabItemV2 {
    val source =
        MisskeyPlatformSpec.antennaTimelineSpec.target(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = UiIcon.Rss.asType(),
        )
    return SourceTimelineTabItemV2.fromSource(source) {
        MisskeyPlatformSpec.antennaTimelineSpec.createPresenter(source.data)
    }
}

internal fun UiList.Channel.toTimelineTabItemV2(accountKey: MicroBlogKey): TimelineTabItemV2 {
    val source =
        MisskeyPlatformSpec.channelTimelineSpec.target(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = banner?.let { IconType.Url(it) } ?: UiIcon.Channel.asType(),
        )
    return SourceTimelineTabItemV2.fromSource(source) {
        MisskeyPlatformSpec.channelTimelineSpec.createPresenter(source.data)
    }
}
