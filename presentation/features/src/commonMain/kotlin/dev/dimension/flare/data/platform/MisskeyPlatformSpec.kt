package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.AccountTimelineSpec
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object MisskeyPlatformSpec : PlatformSpec {
    override val type = MisskeySocialPlatformSpec.type
    override val timelineSpecs = MisskeySocialPlatformSpec.timelineSpecs
    override val metadata = MisskeySocialPlatformSpec.metadata
    override val detector = MisskeySocialPlatformSpec.detector

    override fun agreementUrl(host: String): String? = MisskeySocialPlatformSpec.agreementUrl(host)

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        MisskeySocialPlatformSpec.deepLinkPatterns(host)

    internal val favouriteTimelineSpec =
        AccountTimelineSpec(
            id = "misskey.favourite",
            title = UiStrings.Favourite,
            icon = UiIcon.Favourite.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MisskeyDataSource)
                service.favouriteTimelineLoader()
            },
        )

    internal val hybridTimelineSpec =
        AccountTimelineSpec(
            id = "misskey.hybrid",
            title = UiStrings.Social,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MisskeyDataSource)
                service.hybridTimelineLoader()
            },
        )

    internal val localTimelineSpec =
        AccountTimelineSpec(
            id = "misskey.local",
            title = UiStrings.MastodonLocal,
            icon = UiIcon.Local.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MisskeyDataSource)
                service.localTimelineLoader()
            },
        )

    internal val globalTimelineSpec =
        AccountTimelineSpec(
            id = "misskey.global",
            title = UiStrings.MastodonPublic,
            icon = UiIcon.World.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MisskeyDataSource)
                service.publicTimelineLoader()
            },
        )

    internal val antennaTimelineSpec =
        AccountTimelineSpec(
            id = "misskey.antenna",
            title = UiStrings.Antenna,
            icon = UiIcon.Rss.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory = { service, data ->
                require(service is MisskeyDataSource)
                service.antennasTimelineLoader(data.resourceId)
            },
        )

    internal val channelTimelineSpec =
        AccountTimelineSpec(
            id = "misskey.channel",
            title = UiStrings.Channel,
            icon = UiIcon.Channel.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory = { service, data ->
                require(service is MisskeyDataSource)
                service.channelTimelineLoader(data.resourceId)
            },
        )

    override val legacyTimelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
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

internal fun UiList.Antenna.toTimelineTabItemV2(
    accountKey: MicroBlogKey,
    timelineResolver: TimelineResolver,
): TimelineTabItemV2 {
    val source =
        MisskeyPlatformSpec.antennaTimelineSpec.target(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = UiIcon.Rss.asType(),
        )
    return timelineResolver.toTabItem(source)
}

internal fun UiList.Channel.toTimelineTabItemV2(
    accountKey: MicroBlogKey,
    timelineResolver: TimelineResolver,
): TimelineTabItemV2 {
    val source =
        MisskeyPlatformSpec.channelTimelineSpec.target(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = banner?.let { IconType.Url(it) } ?: UiIcon.Channel.asType(),
        )
    return timelineResolver.toTabItem(source)
}
