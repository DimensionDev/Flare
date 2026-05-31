package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.xqt.XQTBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTDeviceFollowTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTFeaturedTimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object XqtPlatformSpec : PlatformSpec {
    public override val type: PlatformType = PlatformType.xQt
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "X",
            icon = UiIcon.X,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> {
        val profile =
            listOf(
                "https://$xqtHost/{handle}",
                "https://$xqtOldHost/{handle}",
                "https://www.$xqtHost/{handle}",
                "https://www.$xqtOldHost/{handle}",
            )
        val post =
            listOf(
                "https://$xqtHost/{handle}/status/{id}",
                "https://$xqtOldHost/{handle}/",
                "https://www.$xqtHost/{handle}/status/{id}",
                "https://www.$xqtOldHost/{handle}/",
            )
        val media =
            listOf(
                "https://$xqtHost/{handle}/status/{id}/photo/{index}",
                "https://$xqtOldHost/{handle}/status/{id}/photo/{index}",
                "https://www.$xqtHost/{handle}/status/{id}/photo/{index}",
                "https://www.$xqtOldHost/{handle}/status/{id}/photo/{index}",
            )
        return (
            profile.map { profileDeepLink(accountKey, it) } +
                post.map { postDeepLink(accountKey, it) } +
                media.map { postMediaDeepLink(accountKey, it) }
        ).toImmutableList()
    }

    internal val featuredTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.XQT_FEATURED,
            title = UiStrings.Featured,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                XQTFeaturedTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val bookmarkTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.XQT_BOOKMARK,
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                XQTBookmarkTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val deviceFollowTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.XQT_DEVICE_FOLLOW,
            title = UiStrings.Posts,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                XQTDeviceFollowTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.list,
            featuredTimelineSpec,
            bookmarkTimelineSpec,
            deviceFollowTimelineSpec,
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        XQTDataSource(
            accountKey = context.accountKey,
            sourceCredentialFlow = context.credentialFlow(XQTCredential.serializer()),
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")

    private fun profileDeepLink(
        accountKey: MicroBlogKey,
        uriPattern: String,
    ): PlatformDeepLink<XqtProfileDeepLink> =
        PlatformDeepLink(
            uriPattern = uriPattern,
            serializer = XqtProfileDeepLink.serializer(),
            callback = { data ->
                profileRoute(
                    accountKey = accountKey,
                    handle = data.handle,
                )
            },
        )

    private fun postDeepLink(
        accountKey: MicroBlogKey,
        uriPattern: String,
    ): PlatformDeepLink<XqtPostDeepLink> =
        PlatformDeepLink(
            uriPattern = uriPattern,
            serializer = XqtPostDeepLink.serializer(),
            callback = { data ->
                DeeplinkRoute.Status.Detail(
                    accountType = AccountType.Specific(accountKey),
                    statusKey = MicroBlogKey(data.id, accountKey.host),
                )
            },
        )

    private fun postMediaDeepLink(
        accountKey: MicroBlogKey,
        uriPattern: String,
    ): PlatformDeepLink<XqtPostMediaDeepLink> =
        PlatformDeepLink(
            uriPattern = uriPattern,
            serializer = XqtPostMediaDeepLink.serializer(),
            callback = { data ->
                DeeplinkRoute.Media.StatusMedia(
                    accountType = AccountType.Specific(accountKey),
                    statusKey = MicroBlogKey(data.id, accountKey.host),
                    index = data.index,
                    preview = null,
                )
            },
        )
}

@Serializable
private data class XqtProfileDeepLink(
    val handle: String,
)

@Serializable
private data class XqtPostDeepLink(
    val handle: String,
    val id: String,
)

@Serializable
private data class XqtPostMediaDeepLink(
    val handle: String,
    val id: String,
    val index: Int,
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
