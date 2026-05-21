package dev.dimension.flare.data.platform

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.network.misskey.MisskeyPlatformDetector
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
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
        MisskeyService("https://$host/api/").meta(MetaRequest()).render()

    override fun restoreAccount(
        accountKey: MicroBlogKey,
        credentialJson: String,
    ): UiAccount {
        val credential = credentialJson.decodeJson<UiAccount.Misskey.Credential>()
        return UiAccount.Misskey(
            accountKey = accountKey,
            host = credential.host,
            nodeType = credential.nodeType,
        )
    }

    override fun createDataSource(account: UiAccount): MicroblogDataSource {
        require(account is UiAccount.Misskey) {
            "Expected Misskey account for ${type.name}, got ${account.platformType.name}"
        }
        return MisskeyDataSource(
            accountKey = account.accountKey,
            host = account.host,
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
