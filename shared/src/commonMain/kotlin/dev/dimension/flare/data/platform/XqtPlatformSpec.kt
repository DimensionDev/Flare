package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.toSlot
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.xqt.XQTPlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.xqt.XQTBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTDeviceFollowTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTFeaturedTimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal data object XqtPlatformSpec : PlatformSpec {
    override val type = PlatformType.xQt
    override val metadata =
        PlatformTypeMetadata(
            displayName = "X",
            icon = UiIcon.X,
        )
    override val detector: PlatformDetector = XQTPlatformDetector

    override fun agreementUrl(host: String): String = "https://help.x.com/en/rules-and-policies/x-rules"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> {
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
            profile.map { DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url(it)) } +
                post.map { DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url(it)) } +
                media.map { DeepLinkPattern(DeepLinkMapping.Type.PostMedia.serializer(), Url(it)) }
        ).toImmutableList()
    }

    private val featuredTimelineSpec =
        TimelineSpec(
            id = "xqt.featured",
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

    private val bookmarkTimelineSpec =
        TimelineSpec(
            id = "xqt.bookmark",
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

    private val deviceFollowTimelineSpec =
        TimelineSpec(
            id = "xqt.device_follow",
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

    override fun defaultTabs(accountKey: MicroBlogKey): ImmutableList<TimelineSlot> =
        persistentListOf(
            CommonTimelineSpecs.home.target(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon(accountKey.host),
            ).toSlot(),
            featuredTimelineSpec.target(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon(accountKey.host),
            ).toSlot(),
        )

    override fun shortcuts(accountKey: MicroBlogKey): ImmutableList<ShortcutSpec> =
        persistentListOf(
            ShortcutSpec(
                title = UiStrings.Featured,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        featuredTimelineSpec.target(TimelineSpec.AccountBasedData(accountKey)),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Bookmark,
                icon = UiIcon.Bookmark,
                target =
                    ShortcutSpec.Target.Timeline(
                        bookmarkTimelineSpec.target(TimelineSpec.AccountBasedData(accountKey)),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.List,
                icon = UiIcon.List,
                target =
                    ShortcutSpec.Target.Route(
                        DeeplinkRoute.AllLists(accountKey),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.DirectMessage,
                icon = UiIcon.Messages,
                target =
                    ShortcutSpec.Target.Route(
                        DeeplinkRoute.AllDirectMessages(accountKey),
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
