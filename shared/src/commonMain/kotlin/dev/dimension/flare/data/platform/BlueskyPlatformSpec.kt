package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTargetRef
import dev.dimension.flare.data.network.bluesky.BlueskyPlatformDetector
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
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedTimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal data object BlueskyPlatformSpec : PlatformSpec {
    override val type = PlatformType.Bluesky
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Bluesky",
            icon = UiIcon.Bluesky,
        )
    override val detector: PlatformDetector = BlueskyPlatformDetector

    override fun agreementUrl(host: String): String? = "https://bsky.social/about/support/tos"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        buildList {
            add(DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$host/profile/{handle}")))
            add(DeepLinkPattern(DeepLinkMapping.Type.BlueskyPost.serializer(), Url("https://$host/profile/{handle}/post/{id}")))
            if (host == "bsky.social") {
                add(DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://bsky.app/profile/{handle}")))
                add(DeepLinkPattern(DeepLinkMapping.Type.BlueskyPost.serializer(), Url("https://bsky.app/profile/{handle}/post/{id}")))
            }
        }.toImmutableList()

    private val bookmarkTimelineSpec =
        TimelineSpec(
            id = "bluesky.bookmark",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                BlueskyBookmarkTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    private val feedTimelineSpec =
        TimelineSpec(
            id = "bluesky.feed",
            title = UiStrings.Feeds,
            icon = UiIcon.Feeds.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            presenterFactory = {
                BlueskyFeedTimelinePresenter(
                    accountType = AccountType.Specific(it.accountKey),
                    uri = it.resourceId,
                )
            },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.list,
            bookmarkTimelineSpec,
            feedTimelineSpec,
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
                title = UiStrings.List,
                icon = UiIcon.List,
                target = ShortcutSpec.Target.Route(
                    DeeplinkRoute.AllLists(accountKey)
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Feeds,
                icon = UiIcon.Feeds,
                target = ShortcutSpec.Target.Route(
                    DeeplinkRoute.Bluesky.AllFeeds(accountKey)
                ),
            ),
            ShortcutSpec(
                title = UiStrings.Bookmark,
                icon = UiIcon.Bookmark,
                target = ShortcutSpec.Target.Timeline(
                    bookmarkTimelineSpec.target(TimelineSpec.AccountBasedData(accountKey)),
                ),
            ),
            ShortcutSpec(
                title = UiStrings.DirectMessage,
                icon = UiIcon.Messages,
                target = ShortcutSpec.Target.Route(
                    DeeplinkRoute.AllDirectMessages(accountKey)
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
