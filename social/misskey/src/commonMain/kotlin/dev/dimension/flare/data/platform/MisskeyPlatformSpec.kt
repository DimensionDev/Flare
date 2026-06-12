package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.MisskeyLoginProvider
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object MisskeyPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by MisskeyLoginProvider {
    public override val type: PlatformType = PlatformType.Misskey
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Misskey",
            icon = UiIcon.Misskey,
        )

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
            loaderFactory =
                accountLoader<MisskeyDataSource, TimelineSpec.AccountBasedData> {
                    favouriteTimelineLoader()
                },
        )

    internal val hybridTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.MISSKEY_HYBRID,
            title = UiStrings.Social,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<MisskeyDataSource, TimelineSpec.AccountBasedData> {
                    hybridTimelineLoader()
                },
        )

    internal val localTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.MISSKEY_LOCAL,
            title = UiStrings.MastodonLocal,
            icon = UiIcon.Local.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<MisskeyDataSource, TimelineSpec.AccountBasedData> {
                    localTimelineLoader()
                },
        )

    internal val globalTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.MISSKEY_GLOBAL,
            title = UiStrings.MastodonPublic,
            icon = UiIcon.World.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<MisskeyDataSource, TimelineSpec.AccountBasedData> {
                    publicTimelineLoader()
                },
        )

    public val antennaTimelineSpec: TimelineSpec<TimelineSpec.AccountResourceData> =
        TimelineSpec(
            id = TimelineSpecIds.MISSKEY_ANTENNA,
            title = UiStrings.Antenna,
            icon = UiIcon.Rss.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory =
                accountLoader<MisskeyDataSource, TimelineSpec.AccountResourceData> {
                    antennasTimelineLoader(it.resourceId)
                },
        )

    public val channelTimelineSpec: TimelineSpec<TimelineSpec.AccountResourceData> =
        TimelineSpec(
            id = TimelineSpecIds.MISSKEY_CHANNEL,
            title = UiStrings.Channel,
            icon = UiIcon.Channel.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory =
                accountLoader<MisskeyDataSource, TimelineSpec.AccountResourceData> {
                    channelTimelineLoader(it.resourceId)
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

internal fun UiList.Antenna.toTimelineCandidate(accountKey: MicroBlogKey): TimelineCandidate<*> =
    MisskeyPlatformSpec.antennaTimelineSpec
        .candidate(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = UiIcon.Rss.asType(),
        )

internal fun UiList.Channel.toTimelineCandidate(accountKey: MicroBlogKey): TimelineCandidate<*> =
    MisskeyPlatformSpec.channelTimelineSpec
        .candidate(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = banner?.let { IconType.Url(it) } ?: UiIcon.Channel.asType(),
        )
