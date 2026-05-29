package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.network.misskey.JoinMisskeyService
import dev.dimension.flare.data.network.misskey.MisskeyPlatformDetector
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.home.misskey.MissKeyLocalTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MissKeyPublicTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyHybridTimelinePresenter
import dev.dimension.flare.ui.presenter.list.AntennasTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ChannelTimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object MisskeyPlatformSpec : PlatformSpec {
    public override val type: PlatformType = PlatformType.Misskey
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Misskey",
            icon = UiIcon.Misskey,
        )
    override val detector: PlatformDetector = MisskeyPlatformDetector

    override fun agreementUrl(host: String): String? = "https://$host/about"

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://${accountKey.host}/@{handle}",
                serializer = MisskeyProfileDeepLink.serializer(),
                callback = { data -> profileRoute(accountKey, data.handle) },
            ),
            PlatformDeepLink(
                uriPattern = "https://${accountKey.host}/notes/{id}",
                serializer = MisskeyPostDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.Status.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.id, accountKey.host),
                    )
                },
            ),
        )

    internal val favouriteTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.MISSKEY_FAVOURITE,
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
            id = TimelineSpecIds.MISSKEY_HYBRID,
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
            id = TimelineSpecIds.MISSKEY_LOCAL,
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
            id = TimelineSpecIds.MISSKEY_GLOBAL,
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
            id = TimelineSpecIds.MISSKEY_ANTENNA,
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
            id = TimelineSpecIds.MISSKEY_CHANNEL,
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
        MisskeyService("https://$host/api/").meta(MetaRequest()).render()

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        tryRun {
            JoinMisskeyService
                .instances()
                .instancesInfos
                .map {
                    RecommendedInstance(
                        UiInstance(
                            name = it.name,
                            description = it.description,
                            iconUrl = it.meta?.iconURL,
                            domain = it.url,
                            type = type,
                            bannerUrl = it.meta?.bannerURL,
                            usersCount =
                                it.stats?.usersCount ?: it.nodeinfo
                                    ?.usage
                                    ?.users
                                    ?.total ?: 0,
                        ),
                    )
                }
        }.getOrDefault(emptyList())

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource {
        val credential = context.credential(MisskeyCredential.serializer())
        return MisskeyDataSource(
            accountKey = context.accountKey,
            host = credential.host,
            credentialFlow = context.credentialFlow(MisskeyCredential.serializer()),
        )
    }

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}

@Serializable
private data class MisskeyProfileDeepLink(
    val handle: String,
)

@Serializable
private data class MisskeyPostDeepLink(
    val id: String,
)

private fun profileRoute(
    accountKey: MicroBlogKey,
    handle: String,
): DeeplinkRoute {
    val target =
        if (handle.contains('@')) {
            MicroBlogKey.valueOf(handle)
        } else {
            MicroBlogKey(handle, accountKey.host)
        }
    return DeeplinkRoute.Profile.UserNameWithHost(
        accountType = AccountType.Specific(accountKey),
        userName = target.id,
        host = target.host,
    )
}

internal fun UiList.Antenna.toTimelineTabItemV2(accountKey: MicroBlogKey): TimelineTabItemV2 =
    MisskeyPlatformSpec.antennaTimelineSpec
        .tabItem(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = UiIcon.Rss.asType(),
        )

internal fun UiList.Channel.toTimelineTabItemV2(accountKey: MicroBlogKey): TimelineTabItemV2 =
    MisskeyPlatformSpec.channelTimelineSpec
        .tabItem(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = banner?.let { IconType.Url(it) } ?: UiIcon.Channel.asType(),
        )
